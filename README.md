# Counter (fork)

This is a personal fork of [gentlecat/counter](https://github.com/gentlecat/counter) — a simple Android tally counter app. The upstream project is no longer actively maintained; this fork experiments with additional features on top of it.

> [!NOTE]
> The changes in this fork were implemented with [Claude Code](https://claude.com/claude-code) (Opus 4.7) under direction from the fork maintainer.

## Changes in this fork

- **Per-counter change history.** Every update (`CREATED`, `INCREMENT`, `DECREMENT`, `RESET`, `EDITED`, `DELETED`) is recorded with its timestamp and the resulting value. History is kept in a separate SharedPreferences file alongside the existing counter values.
- **Versioned CSV export (v2).** Export now produces a self-describing file with a `# Version: 2` header and per-counter blocks:
  - `[Counter]` — current name, value, and last-update timestamp.
  - `[History]` — every recorded event for that counter.
- **CSV import.** Settings → "Import counters" opens a file picker (Storage Access Framework) and restores counters and their history from a v2 export. Unknown versions are rejected with a clear toast.
- **Legacy data bootstrap.** Counters that existed before the upgrade get a synthetic `CREATED` event derived from their existing last-update timestamp, so the history is non-empty from day one.
- **Manual GitHub Actions trigger.** The Android CI workflow now supports `workflow_dispatch`, and manual runs upload the debug APK as a build artifact.

The change history is not exposed in the in-app UI — it surfaces only through the CSV export, which keeps the original UX unchanged.

---

# Original README

# Counter

Simple [tally counter](https://en.wikipedia.org/wiki/Tally_counter) for Android. It makes counting easier! You can have multiple counters with their own names and values. Values can be changed using the volume buttons or by tapping on the screen. All your data can be exported in CSV format.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80" />](https://f-droid.org/packages/me.tsukanov.counter)

> [!WARNING]  
> **Note that the app is no longer available from the Google Play Store.** See [the section below](#google-play) for details.

---

<img src="metadata/en-US/images/phoneScreenshotsFramed/5_framed.png" width="150" />&nbsp;&nbsp;&nbsp;&nbsp;<img src="metadata/en-US/images/phoneScreenshotsFramed/1_framed.png" width="150" />&nbsp;&nbsp;&nbsp;&nbsp;<img src="metadata/en-US/images/phoneScreenshotsFramed/2_framed.png" width="150" />&nbsp;&nbsp;&nbsp;&nbsp;<img src="metadata/en-US/images/phoneScreenshotsFramed/3_framed.png" width="150" />&nbsp;&nbsp;&nbsp;&nbsp;<img src="metadata/en-US/images/phoneScreenshotsFramed/4_framed.png" width="150" />

## Contributing

> [!IMPORTANT]
> **🚧 This app is not actively maintained.**

Feel free to make improvements, report issues, and create pull requests. If you want to help with
translation, go to project's [page on Crowdin](http://crowdin.net/project/simple-counter) (please,
don't translate resources directly).

## Credits

Huge thanks to all [contributors](https://github.com/gentlecat/counter/contributors)
and [translators](https://crowdin.net/project/simple-counter).

* Application icon made by @armand-leguillou
* Sounds from [adobeflash.com](https://www.adobeflash.com/download/sounds/clicks/)

## Google Play

Google decided to terminate my developer account for no apparent reason, so Counter is no longer available on the Play Store. I've never received any notices or warnings before this. Counter was the only app that I [published on the Play Store](https://web.archive.org/web/20250121131910/https://play.google.com/store/apps/details?id=me.tsukanov.counter) for over 12 years. Before removal, it was installed on over 500k active devices, was downloaded over 1 million times and had 4.5 rating with almost 6.5k reviews.

I tried going through the appeal process, but they refuse to tell me what the issue is. In fact, it seems like I'm forever banned from publishing anything on their store.

This is the notice which I received:

![](https://github.com/user-attachments/assets/353c5c36-b043-420a-b1b9-00da3333ea40)

Some screenshots from by developer dashboard before I lost access to it:

<img width="768" alt="summary" src="https://github.com/user-attachments/assets/7dfe7cc3-f8fe-4828-8804-3df5d3d55662" />

<img width="1199" alt="installs_5y" src="https://github.com/user-attachments/assets/fd2b36ed-eaaa-4a8c-bfb5-fd13a1238943" />
