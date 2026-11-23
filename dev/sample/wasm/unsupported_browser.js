const unhandledError = (event, error) => {
    if (typeof WebAssembly !== "undefined" && error instanceof WebAssembly.CompileError) {
        const warning = document.getElementById("warning");
        if (warning != null) {
            warning.style.display = "initial";
        }

        // Hide Webpack Overlay
        const webpackOverlay = document.getElementById("webpack-dev-server-client-overlay");
        if (webpackOverlay != null) {
          webpackOverlay.style.display = "none";
        }

        const spinner = document.getElementById("spinner");
        if (spinner != null) {
            spinner.style.display = "none";
        }

        const sample = document.getElementById("Sample");
        if (sample != null) {
            sample.style.display = "none";
        }
    }
}
addEventListener("error", (event) => unhandledError(event, event.error));
addEventListener("unhandledrejection", (event) => unhandledError(event, event.reason));
