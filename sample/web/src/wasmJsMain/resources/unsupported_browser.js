const hide = (id) => {
    const element = document.getElementById(id);
    if (element != null) {
        element.style.display = "none";
    }
}

const unhandledError = (event, error) => {
    if (typeof WebAssembly !== "undefined" && error instanceof WebAssembly.CompileError) {
        const warning = document.getElementById("warning");
        if (warning != null) {
            warning.style.display = "initial";
        }

        // Hide Webpack Overlay
        hide("webpack-dev-server-client-overlay");

        hide("spinner");
        hide("Sample");
    }
}
addEventListener("error", (event) => unhandledError(event, event.error));
addEventListener("unhandledrejection", (event) => unhandledError(event, event.reason));
