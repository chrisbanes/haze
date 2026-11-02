function onLoadFinished() {
    document.dispatchEvent(new Event("app-loaded"));
}

function queryParameter(name) {
    return new URLSearchParams(window.location.search).get(name);
}

document.addEventListener("app-loaded", function() {
    document.getElementById("spinner").style.display = "none";
});
