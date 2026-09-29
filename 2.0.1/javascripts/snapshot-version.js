(() => {
  const scriptUrl = document.currentScript?.src
  if (scriptUrl == null) return

  const versionElements = document.querySelectorAll("[data-haze-snapshot-version]")
  if (versionElements.length === 0) return

  const metadataUrl = new URL("snapshot-metadata.xml", scriptUrl)

  async function displaySnapshotVersion() {
    try {
      const response = await fetch(metadataUrl, { cache: "no-cache" })
      if (!response.ok) return

      const metadata = new DOMParser().parseFromString(
        await response.text(),
        "application/xml",
      )
      if (metadata.querySelector("parsererror") != null) return

      const version = metadata.querySelector("metadata > versioning > latest")?.textContent?.trim()
      if (version == null || !/^[0-9A-Za-z][0-9A-Za-z._+-]*-SNAPSHOT$/.test(version)) return

      versionElements.forEach((element) => {
        element.textContent = version
      })
      document.querySelectorAll("[data-haze-snapshot-version-container]").forEach((element) => {
        element.hidden = false
      })
    } catch {
      // The static metadata link remains available if the generated file cannot be read.
    }
  }

  void displaySnapshotVersion()
})()
