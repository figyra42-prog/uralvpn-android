# UralVPN: notice about the origin and the changes

UralVPN is a modified version of free software and is distributed under the same terms, the
GNU General Public License version 3 or later (see the `LICENSE` file in this repository).

**Origin**
- The app (`uralvpn-android`) is based on `sing-box-for-android` (SFA) by nekohasekai / SagerNet.
- The core (`uralvpn-core`) is based on a fork of `sing-box` made by the Hiddify project, which in turn
  is based on `sing-box` by nekohasekai / SagerNet.
- The icons, the colors and the texts of the interface are taken from the earlier UralVPN app,
  which is itself a GPLv3 fork of v2rayNG.

**Changes made by UralVPN** (the complete history is in the git log of this repository):
- the home screen, the side menu, the settings and the subscription card in the style of UralVPN;
- the subscription is merged from the panel's sing-box profile and its Xray JSON (direct servers plus
  the CDN servers, with the autopilot "direct -> CDN -> back to direct");
- the Russian sites go directly (the rules come from the subscription);
- the ClientHello fragmentation for the direct Reality servers, the ping test with one retry,
  the background update of the subscription, the application name and the update check from this repository.

**Source code.** The source of every release is in this repository (the app) and in
`figyra42-prog/uralvpn-core` (the core). The version of the core used by a release is shown in the
build log of the workflow `UralVPN build`.

**Name.** In accordance with the additional condition of the original license, UralVPN is **not**
affiliated with and is not endorsed by the authors of `sing-box`, `SFA` or `Hiddify`; their names
are used only to tell where the code comes from.

---

UralVPN - изменённая версия свободного программного обеспечения, распространяется на тех же условиях
(GNU GPL v3 или новее, файл `LICENSE`). Исходный код каждой сборки открыт: приложение - в этом
репозитории, ядро - в `figyra42-prog/uralvpn-core`. UralVPN не связан с авторами sing-box, SFA и Hiddify.
