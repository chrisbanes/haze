# Release changelog

The project maintains `CHANGELOG.md` following the [Keep a Changelog](https://keepachangelog.com)
format. When preparing releases, update the changelog with entries from GitHub releases. Changes
should be categorized under standard headings: Added, Changed, Fixed, Deprecated, Removed, and
Security. Each version entry includes the release date in `YYYY-MM-DD` format, selected links to pull
requests, and contributor acknowledgments. The changelog can be regenerated or updated using the
GitHub REST API to fetch release notes: `curl -s
"https://api.github.com/repos/chrisbanes/haze/releases?per_page=100"`.

Write changelogs around what the release does for users: new capabilities, observable improvements,
and necessary upgrade actions. Summarize related work rather than listing every PR or implementation
detail. Include bug fixes only for defects present in previously released public behavior; omit
fixes to features introduced in the same release. Use PR links selectively and preserve contributor
credits.
