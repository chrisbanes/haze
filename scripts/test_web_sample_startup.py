import json
import re
import shutil
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).parent.parent
RESOURCES = ROOT / "sample/web/src/wasmJsMain/resources"
HANDLER = RESOURCES / "unsupported_browser.js"
INDEX = RESOURCES / "index.html"
MAIN_KT = ROOT / "sample/web/src/wasmJsMain/kotlin/dev/chrisbanes/haze/sample/Main.kt"

# Runs the real handler in a vm with a mock DOM; argv: handler, element ids, listener, payload kind.
HARNESS = r"""
const fs = require("fs");
const vm = require("vm");
const [handler, ids, listenerType, kind] = process.argv.slice(1);
const elements = {};
for (const id of JSON.parse(ids)) elements[id] = {style: {display: id === "warning" ? "none" : ""}};
const initial = Object.fromEntries(Object.entries(elements).map(([k, v]) => [k, v.style.display]));
const listeners = {};
const context = vm.createContext({
  WebAssembly,
  document: {getElementById: (id) => elements[id] ?? null},
  addEventListener: (type, fn) => { listeners[type] = fn; },
});
vm.runInContext(fs.readFileSync(handler, "utf8"), context);
const payloads = {
  compile: new WebAssembly.CompileError("x"),
  other: new Error("x"),
  none: undefined,
};
let thrown = null;
if (listenerType) {
  const key = listenerType === "error" ? "error" : "reason";
  try { listeners[listenerType]({[key]: payloads[kind]}); } catch (e) { thrown = String(e); }
}
const displays = Object.fromEntries(Object.entries(elements).map(([k, v]) => [k, v.style.display]));
console.log(JSON.stringify({listenerTypes: Object.keys(listeners), thrown, initial, displays}));
"""


def html_ids():
    return re.findall(r'\bid="([^"]+)"', INDEX.read_text())


def mount_id():
    return re.search(r'viewportContainerId\s*=\s*"([^"]+)"', MAIN_KT.read_text()).group(1)


class WebSampleStartupTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(shutil.which("node"), "node is required to run this test")

    def run_handler(self, listener=None, kind="compile", ids=None):
        result = subprocess.run(
            ["node", "-e", HARNESS, str(HANDLER), json.dumps(html_ids() if ids is None else ids),
             listener or "", kind],
            check=True, capture_output=True, text=True,
        )
        return json.loads(result.stdout)

    def assert_fallback_shown(self, listener):
        result = self.run_handler(listener)
        self.assertIsNone(result["thrown"])
        self.assertEqual(result["displays"]["warning"], "initial")
        self.assertEqual(result["displays"]["spinner"], "none")
        self.assertEqual(result["displays"][mount_id()], "none")

    def test_compileErrorEvent_showsWarningAndHidesSample(self):
        self.assert_fallback_shown("error")

    def test_compileErrorRejection_showsWarningAndHidesSample(self):
        self.assert_fallback_shown("unhandledrejection")

    def test_otherStartupError_leavesSampleVisible(self):
        for listener, kind in [("error", "other"), ("unhandledrejection", "none")]:
            result = self.run_handler(listener, kind)
            self.assertIsNone(result["thrown"])
            self.assertEqual(result["displays"], result["initial"])

    def test_missingElements_doesNotThrow(self):
        for listener in ["error", "unhandledrejection"]:
            self.assertIsNone(self.run_handler(listener, ids=[])["thrown"])

    def test_handler_registersBothStartupEvents(self):
        self.assertEqual(
            sorted(self.run_handler()["listenerTypes"]), ["error", "unhandledrejection"]
        )

    def test_indexHtml_loadsHandlerBeforeSample(self):
        html = re.sub(r"<!--.*?-->", "", INDEX.read_text(), flags=re.S)
        scripts = re.findall(r'<script[^>]*\bsrc="([^"]+)"', html)
        self.assertLess(scripts.index("unsupported_browser.js"), scripts.index("sample.js"))
        self.assertIn(mount_id(), html_ids())


if __name__ == "__main__":
    unittest.main()
