function onLoadFinished() {
    document.dispatchEvent(new Event("app-loaded"));
}

document.addEventListener("app-loaded", function() {
    document.getElementById("spinner").style.display = "none";
});
