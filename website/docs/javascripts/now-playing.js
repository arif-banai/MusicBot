// Ticks the home page's now-playing example forward, like the bot's message does.
// Does nothing on other pages, or when the reader prefers reduced motion.
(function () {
  var np = document.querySelector(".oa-np");
  if (!np) return;
  if (window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

  var pos = np.querySelector(".oa-np__pos");
  var bar = np.querySelector(".oa-np__bar");
  if (!pos || !bar) return;

  var start = parseInt(pos.dataset.start, 10);
  var end = parseInt(pos.dataset.end, 10);
  var segments = parseInt(bar.dataset.segments, 10) || 12;
  var now = start;

  function clock(s) {
    var m = Math.floor(s / 60);
    var r = s % 60;
    // Same as TimeUtil.formatTime: minutes and seconds both zero-padded.
    return (m < 10 ? "0" : "") + m + ":" + (r < 10 ? "0" : "") + r;
  }

  // Same rule as FormatUtil.progressBar: the dot sits at segment floor(fraction * 12).
  function progressBar(fraction) {
    var dot = Math.floor(fraction * segments);
    var out = "";
    for (var i = 0; i < segments; i++) out += i === dot ? "🔘" : "▬";
    return out;
  }

  function render() {
    pos.textContent = clock(now);
    bar.textContent = progressBar(now / end);
  }

  render();
  window.setInterval(function () {
    now = now >= end ? 0 : now + 1;
    render();
  }, 1000);
})();
