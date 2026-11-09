# How to put Mvoon on the Play Store 🚀

Think of Google Play as a giant toy shop. To put your toy (the app) on the shelf you need:
a **shop account**, the **toy in its box** (the app file), a **label** (pictures and text),
honest **answers to the shop's safety questions**, and **12 friends who test it for 2 weeks**.

## ✅ Already done for you

- New name **Mvoon** + crescent-moon logo. mvoon.com and mvoon.app were not registered on 3 Nov 2026 and no
  Play app uses the name. ⚠️ A US trademark **MVON** (developer software) is one letter away, so ask
  a trademark lawyer before you spend money on the brand.
- Package name `app.mvoon`, version 1.6.0, built for Android 16 (Google's 2026 rule).
- The box: **`dist/Mvoon-1.6.0.aab`** (this is the file Play wants, not the APK).
- The label: text in `store/listing.md`, icon, feature graphic and screenshots in `store/`.
- Privacy policy: `PRIVACY.md`. Licence: `LICENSE` (GPL-3.0) and `THIRD_PARTY_NOTICES.md`.
- In the app: Settings → About shows the source-code link, the privacy policy and every licence.
- The recipe (source code) zip: `dist/Mvoon-1.6.0-source.zip` (no secrets inside).

## 1. Grab the name before someone else does (today, ~$25/year)

- Buy **mvoon.com** and **mvoon.app** at any domain shop (Cloudflare, Porkbun, Namecheap…).
- Make a free **GitHub** organisation (or account) named **`mvoon`**. The app already links to
  `github.com/mvoon/mvoon`. Using a different name? Change the two lines at the bottom of
  `gradle.properties` and rebuild.
- Later, if the app does well: register the trademark "Mvoon" (class 9, software) in your
  country. India: ipindia.gov.in. Search first at branddb.wipo.int. A lawyer can do this for you.

## 2. Hide your secret key 🔑

`signing/mvoon-upload.jks` + `keystore.properties` are the key that proves updates come from you.
Copy **both** to two safe places (a USB stick and an encrypted cloud folder). **Never** upload
them anywhere public. `.gitignore` already keeps them out of GitHub.

## 3. Share the recipe (the FFmpeg rule) 📖

Mvoon uses FFmpeg, which comes with a rule: *if you give people the cake, you must also give
them the recipe.* So the code has to be public **before** the app is.

1. On GitHub, create a **public** repository `mvoon/mvoon`.
2. In this folder, run:
   ```
   git init -b main
   git add .
   git commit -m "Mvoon 1.6.0"
   git remote add origin https://github.com/mvoon/mvoon.git
   git push -u origin main
   ```
3. On GitHub, make a **Release** called `v1.6.0` and attach:
   - `dist/Mvoon-1.6.0-source.zip`;
   - the 6 files that `python tools/fetch_third_party_sources.py` downloads into `dist/sources/`.
4. Open `github.com/mvoon/mvoon` and `…/blob/main/PRIVACY.md` and check that both pages load.

## 4. Open your shop account 🧾

Go to **play.google.com/console** → choose **Personal** → pay **$25 once** → verify your ID and
phone number. Verification can take a few days.

## 5. Create the app and answer the safety questions 📝

In Play Console: **Create app** → name "Mvoon Player" → App → Free. Then go to **App content**:

| Question | Answer |
|---|---|
| Privacy policy | `https://github.com/mvoon/mvoon/blob/main/PRIVACY.md` |
| Ads | No ads |
| App access | All functionality is available without special access |
| Content rating | Fill in the quiz honestly (no violence etc.). The app plays the user's own files. |
| Target audience | 13 and older (it is not a kids' app) |
| Data safety | Collects **no** data, shares **no** data |
| Photo & video permissions | "Mvoon is a video player. Its core feature is a library of **all** the user's videos, with folders, recently watched videos and resume positions, which the Photo Picker cannot provide." |
| Foreground service | Type **Media playback**. Add a link to a short video (an unlisted YouTube video is fine) showing *you* pressing play on a song, then going to the home screen while the music keeps playing. |
| News / Government / Finance / Health | No |

Then **Main store listing**: paste the text from `store/listing.md` and upload the pictures from `store/`.
Category: **Video Players & Editors**. Add your contact email.

## 6. The 2-week test with 12 friends 👫

New personal accounts must do this once per app.

1. **Testing → Closed testing → Create track.** Add **12 or more** testers by Gmail address.
2. **Create release** → upload `dist/Mvoon-1.6.0.aab` → accept **Play App Signing**.
3. Send testers the opt-in link. They install Mvoon and **keep it for 14 days in a row**.
4. After 14 days: **Dashboard → Apply for production** and answer the short questions.

## 7. Go live 🎉

**Production → Create release** → upload the same `.aab` → **Roll out**. Google reviews it,
usually within a few days. Then it is in the shop!

## Every update after that 🔁

1. In `app/build.gradle.kts`, raise `versionCode` by 1 and change `versionName` (e.g. `1.4.1`).
2. Build the box: `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
3. Push the code to GitHub, and make a Release with `python tools/make_source_zip.py`'s zip (the recipe rule again).
4. Upload the new `.aab` in Play Console.
5. Each August, Google asks for the newest Android version. If Play Console warns you, raise `targetSdk`.

## Things only you can do

Buy the domains, create the GitHub and Play accounts, verify your identity, find 12 testers,
record the 1-minute playback video, and test on a real phone (I could only test on an emulator).
