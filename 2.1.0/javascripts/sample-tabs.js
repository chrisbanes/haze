(() => {
  document.querySelectorAll("iframe[data-haze-sample-src]").forEach((frame) => {
    const block = frame.closest(".tabbed-block")
    const tabs = block.closest(".tabbed-set")
    const blocks = [...block.parentElement.children]
    const inputs = tabs.querySelectorAll(":scope > input")
    const input = inputs[blocks.indexOf(block)]

    function update() {
      if (input.checked) {
        if (!frame.hasAttribute("src")) frame.src = frame.dataset.hazeSampleSrc
      } else {
        frame.removeAttribute("src")
      }

      blocks.forEach((panel, index) => {
        panel.querySelectorAll("video").forEach((video) => {
          if (inputs[index].checked) {
            void video.play().catch(() => {})
          } else {
            video.pause()
          }
        })
      })
    }

    tabs.addEventListener("change", update)
    update()
  })
})()
