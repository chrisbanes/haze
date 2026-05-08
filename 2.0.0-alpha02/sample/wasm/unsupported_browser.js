const unhandledError = (event, error) => {
    if (error instanceof WebAssembly.CompileError) {
        document.getElementById("warning").style.display = "initial";

        // Hide Webpack Overlay
        const webpackOverlay = document.getElementById("webpack-dev-server-client-overlay");
        if (webpackOverlay != null) {
          webpackOverlay.style.display = "none";
        }

        document.getElementById("spinner").style.display = "none";
        document.getElementById("TiamatTarget").style.display = "none";
    }
}
addEventListener("error", (event) => unhandledError(event, event.error));
addEventListener("unhandledrejection", (event) => unhandledError(event, event.reason));
