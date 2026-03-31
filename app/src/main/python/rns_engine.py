import sys, os, csv, io, json, signal, warnings, shutil, traceback, time
from types import ModuleType
import importlib.util, importlib.machinery

warnings.filterwarnings("ignore", category=DeprecationWarning)
warnings.filterwarnings("ignore", category=ResourceWarning)

# --- 1. THE ULTIMATE ANDROID MOCKS (LOCKED) ---
class Dummy:
    def __init__(self, name="Dummy"):
        self.__name__ = name
        self.__spec__ = importlib.machinery.ModuleSpec(name, None)
    def __getattr__(self, name): return self
    def __call__(self, *args, **kwargs): return self
    def __len__(self): return 0
    def __getitem__(self, index): return self

def mock_module(name):
    mock = Dummy(name)
    sys.modules[name] = mock
    return mock

mock_module("usbserial4a").serial4a = Dummy("serial4a")
mock_module("jnius").autoclass = lambda x: Dummy("DummyClass")
mock_module("usb4a").usb = Dummy("usb4a.usb")
sys.modules["usb4a.usb"] = sys.modules["usb4a"].usb

_orig_find_spec = importlib.util.find_spec
def _mock_find_spec(name, package=None):
    if name in["usbserial4a", "jnius", "usb4a", "usb4a.usb"]: return sys.modules[name].__spec__
    return _orig_find_spec(name, package)
importlib.util.find_spec = _mock_find_spec

# --- 2. IMPORT RNS ---
import RNS
from LXMF import LXMRouter, LXMessage
from RNS.Interfaces.Android.RNodeInterface import RNodeInterface
from RNS.Interfaces.Interface import Interface

signal.signal = lambda sig, handler: None

kotlin_cb = None
local_destination = None
router = None
active_ifac = None 

def start_engine(service_obj, storage_path):
    global kotlin_cb, local_destination, router
    kotlin_cb = service_obj
    try:
        rns_dir = os.path.join(storage_path, ".reticulum")
        lxmf_dir = os.path.join(storage_path, ".lxmf")
        if not os.path.exists(rns_dir): os.makedirs(rns_dir)
        if not os.path.exists(lxmf_dir): os.makedirs(lxmf_dir)
        
        # Clean config to prevent port collisions
        with open(os.path.join(rns_dir, "config"), "w") as f:
            f.write("[reticulum]\nenable_transport = True\nshare_instance = Yes\n\n[interfaces]\n")

        RNS.Reticulum(configdir=rns_dir)
        
        # Identity is stored in the root storage_path to keep it safe from config wipes
        id_path = os.path.join(storage_path, "storage_identity")
        local_id = RNS.Identity.from_file(id_path) if os.path.exists(id_path) else RNS.Identity()
        if not os.path.exists(id_path): local_id.to_file(id_path)
        
        router = LXMRouter(identity=local_id, storagepath=lxmf_dir)
        local_destination = router.register_delivery_identity(local_id, display_name="Harvester")
        
        addr = RNS.hexrep(local_destination.hash, False)
        service_obj.onStatusUpdate(f"RNS Online: {addr}")
    except Exception as e:
        service_obj.onStatusUpdate(f"Init Error: {str(e)}")

def inject_rnode(radio_params_json):
    global active_ifac
    try:
        params = json.loads(radio_params_json)
        # LOCKED RADIO SETTINGS: 433MHz, SF8, CR6
        ictx = {
            "name": "Android RNode Bridge",
            "type": "RNodeInterface",
            "interface_enabled": True,
            "outgoing": True,
            "tcp_host": "127.0.0.1",
            "tcp_port": 7633,
            "frequency": int(params.get("freq", 433000000)),
            "bandwidth": int(params.get("bw", 125000)),
            "txpower": int(params.get("tx", 17)),
            "spreadingfactor": int(params.get("sf", 8)),
            "codingrate": int(params.get("cr", 6)),
            "flow_control": False
        }
        
        active_ifac = RNodeInterface(RNS.Transport, ictx)
        active_ifac.mode = Interface.MODE_FULL
        active_ifac.IN = True
        active_ifac.OUT = True
        
        RNS.Transport.interfaces.append(active_ifac)
        
        time.sleep(1)
        if local_destination: local_destination.announce()
        return "RNode Active"
    except Exception as e:
        return f"Link Failed: {str(e)}"

def send_report(target_hex, harvester_nick, block_id, ripe, empty, lat, lon, ts_str, photo_b64):
    global router, local_destination
    try:
        # Generate unique report ID
        report_id = f"R{int(time.time())}"
        
        # ALIGNED CSV SCHEMA: id, harvester_id, block_id, ripe_bunches, empty_bunches, latitude, longitude, timestamp, photo_file
        csv_payload = f"id,harvester_id,block_id,ripe_bunches,empty_bunches,latitude,longitude,timestamp,photo_file\n"
        csv_payload += f"{report_id},{harvester_nick},{block_id},{ripe},{empty},{lat},{lon},{ts_str},{photo_b64}"
        
        dest_hash = bytes.fromhex(target_hex)
        # Check if we know this destination's identity
        dest_id = RNS.Identity.recall(dest_hash)
        
        target = RNS.Destination(dest_id, RNS.Destination.OUT, RNS.Destination.SINGLE, "lxmf", "delivery")
        if dest_id is None: target.hash = dest_hash
        
        lxm = LXMessage(target, local_destination, csv_payload, title="Harvest Sync")
        router.handle_outbound(lxm)
        print(f"RNS-LOG: Report {report_id} pushed to router.")
        return "Report Sent"
    except Exception as e:
        print(f"RNS-LOG: Send failed: {e}")
        return f"Error: {str(e)}"

def announce_now():
    try:
        if local_destination:
            local_destination.announce()
            print("RNS-LOG: Manual Announce broadcasted.")
    except Exception as e:
        print(f"RNS-LOG: Announce Error: {e}")