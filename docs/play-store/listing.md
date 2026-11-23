# Google Play listing – Cabin Comfort for MG

## App details
- **App name (max 30):** Cabin Comfort for MG
- **Category:** Auto & Vehicles
- **Tags:** Vehicle remote control, Electric vehicles
- **Contains ads:** No
- **In-app purchases:** No
- **Contact email:** cm@rhea.dk
- **Privacy policy URL:** https://api.rhea.dk/privacy-policy_mgmanager

---

## English (en-GB, default)

### Short description (max 80)
Seats, wheel, windows and cabin heat in one tap — comfort profiles for your MG

### Full description (max 4000)
No more digging through menus every frosty morning.

Cabin Comfort for MG lets you save your perfect cabin as a profile – and start everything with a single tap: cabin heat, defrost, heated seats, steering wheel and rear window, all at once.

★ ENVIRONMENT PROFILES
• "Warm up" for cold mornings: cabin heat, windscreen defrost, seat heating, heated steering wheel and rear window – in one tap
• "Chill" for hot days: cool the cabin before you get in
• Edit each profile once and forget about it: temperature, fan speed, defrost, seat levels for driver and passenger, steering wheel, rear window
• Switch profiles straight from the home screen

★ RUNS LONGER THAN ONE CYCLE
Your MG stops the climate on its own after about 10 minutes. Cabin Comfort for MG can keep it going for up to 4 runs in a row (around 40 minutes) and restarts each run automatically. A minute before the last run ends, you're asked whether to continue.

★ ONE TAP, NO MENUS
Instead of turning on climate, then seats, then the wheel, then the rear window one by one, you just tap your profile. Done.

★ SMART SUGGESTIONS
The app checks the outside temperature and suggests the profile that fits. Nothing starts until you say so.

★ RELIABLE IN THE BACKGROUND
MG's servers don't always answer the first time. Every command is queued and quietly retried, so you don't have to keep tapping. A notification shows progress while the car is being heated or cooled.

★ CAR STATUS AT A GLANCE
Outside and cabin temperature and battery level when you open the app.

★ PRIVATE BY DESIGN
• No ads, no analytics, no servers of our own
• Your login is stored encrypted on your phone and only sent to MG's own iSMART servers

REQUIREMENTS
• An MG with remote climate control and an active MG iSMART account
• Log in with the same email/phone and password as the official iSMART app
• Supports the Europe, Australia/NZ, Türkiye and Brazil iSMART servers

DISCLAIMER
Cabin Comfort for MG is an independent, unofficial app. It is not affiliated with, endorsed by or sponsored by MG Motor, SAIC Motor or iSMART. MG and iSMART are trademarks of their respective owners. Built on the open-source SAIC-iSmart-API project.

### Release notes (beta 1.0)
First beta: "Warm up" and "Chill" comfort profiles, editable settings, temperature-based suggestions, background retry and climate progress notification. Feedback welcome!

---

## Dansk (da-DK)

### Kort beskrivelse (max 80)
Sæder, rat, ruder og kabinevarme med ét tryk — komfortprofiler til din MG

### Fuld beskrivelse
Slut med at rode rundt i menuer hver frostklar morgen.

Med Cabin Comfort for MG gemmer du din perfekte kabine som en profil – og starter det hele med ét tryk: kabinevarme, afrimning, sædevarme, rat- og bagrudevarme på én gang.

★ KOMFORTPROFILER
• "Warm up" til kolde morgener: kabinevarme, afrimning af forruden, sædevarme, rat- og bagrudevarme – med ét tryk
• "Chill" til varme dage: køl kabinen ned, før du sætter dig ind
• Indstil hver profil én gang: temperatur, blæser, afrimning, sædevarme til fører og passager, rat og bagrude
• Skift profil direkte fra forsiden

★ KØRER LÆNGERE END ÉN CYKLUS
Din MG slukker selv klimaanlægget efter ca. 10 minutter. Cabin Comfort for MG kan holde det kørende i op til 4 omgange i træk (ca. 40 minutter) og genstarter automatisk. Et minut før sidste omgang slutter, bliver du spurgt, om den skal fortsætte.

★ ÉT TRYK, INGEN MENUER
I stedet for at tænde klima, så sæder, så rat og så bagrude hver for sig, trykker du bare på din profil. Færdig.

★ SMARTE FORSLAG
Appen ser på udetemperaturen og foreslår den profil, der passer. Intet starter, før du siger ja.

★ PÅLIDELIG I BAGGRUNDEN
MG's servere svarer ikke altid i første forsøg. Alle kommandoer sættes i kø og prøves igen automatisk, så du ikke skal blive ved med at trykke. En notifikation viser fremskridtet, mens bilen varmes op eller køles ned.

★ BILENS STATUS MED ÉT BLIK
Ude- og kabinetemperatur samt batteriniveau, når du åbner appen.

★ PRIVATLIV FØRST
• Ingen reklamer, ingen analyse, ingen egne servere
• Dit login gemmes krypteret på telefonen og sendes kun til MG's egne iSMART-servere

KRAV
• En MG med fjernstyret klima og en aktiv MG iSMART-konto
• Log ind med samme e-mail/telefon og adgangskode som i den officielle iSMART-app
• Understøtter iSMART-serverne for Europa, Australien/NZ, Tyrkiet og Brasilien

ANSVARSFRASKRIVELSE
Cabin Comfort for MG er en uafhængig, uofficiel app og er ikke tilknyttet, godkendt eller sponsoreret af MG Motor, SAIC Motor eller iSMART. MG og iSMART er varemærker tilhørende deres respektive ejere. Bygget på open source-projektet SAIC-iSmart-API.

### Udgivelsesnoter (beta 1.0)
Første beta: "Warm up"- og "Chill"-profiler, redigerbare indstillinger, forslag ud fra udetemperatur, automatisk genforsøg og notifikation under opvarmning. Feedback er meget velkommen!

---

## Graphics checklist
| Asset | Spec | Status |
|---|---|---|
| App icon | 512×512 PNG, 32-bit | export from sun & snow icon |
| Feature graphic | 1024×500 PNG/JPG | `feature-graphic.png` |
| Phone screenshots | 2–8, 9:16, min 1080px short side recommended | `screenshots/` |

## Data safety form (suggested answers)
- Collects data: **Yes** – Personal info › Email address / Phone number, plus password, used only to log in (App functionality). Vehicle status (battery, temperatures) is fetched and kept on-device. No location.
- Shared with third parties: **No** (sent only to MG iSMART as the service the user signs into – counts as user-initiated)
- Encrypted in transit: **Yes**
- Users can request deletion: **Yes** – log out / uninstall removes all data
- Not used for ads or analytics

## Policy declarations to expect
- **SCHEDULE_EXACT_ALARM**: Play asks why. Answer: alarm used to ask the user one minute before climate ends whether to continue (user-facing timer). If not strictly needed, switching to `USE_EXACT_ALARM`-free inexact scheduling avoids the declaration.
- **App access**: reviewers need an iSMART login – provide a test account or explain it requires a real MG (use mock server video if needed).
- **Trademark**: keep the disclaimer; "for MG" phrasing and no MG logo in icon/graphics.
