# Chunky (privates Admin-Plugin) – Paper 1.21.11

**Wichtig vorab – Teststatus (ehrlich):** Dieses Projekt wurde in einer Umgebung ohne Maven, ohne Netzwerk und ohne Paper-API geschrieben.
Es wurde **nicht gebaut und nicht auf einem Server getestet**. Geprüft wurde nur, dass der Quellcode syntaktisch parsebar ist.
Die fertige `Chunky.jar` entsteht erst bei dir mit `mvn clean package`. Siehe Abschnitt „Tests".

## Voraussetzungen
- Paper **1.21.11**, Java **21**
- Zum Bauen: JDK 21 + Maven 3.8+ (Internetzugang zu `repo.papermc.io`)

## Bauen
```
mvn clean package
```
Ergebnis: `target/Chunky.jar`. Falls Maven die Paper-API nicht findet, `paper.version` in der `pom.xml` prüfen (`1.21.11-R0.1-SNAPSHOT`).

## Installation
1. Server stoppen.
2. Alte `Chunky*.jar` aus `plugins/` entfernen. **Achtung:** Das bekannte Pre-Generator-Plugin „Chunky" hat denselben Namen – nicht beide gleichzeitig installieren.
3. `Chunky.jar` nach `plugins/` kopieren, Server starten.
4. Dich einloggen, `/chunky uuid` ausführen. Steht dort „Owner laut config: nein", die angezeigte UUID in `plugins/Chunky/config.yml` unter `owner-uuids` eintragen und in der Konsole `chunky reload` ausführen.
5. Konsole kontrollieren: Es dürfen keine Meldungen wie „fehlt in der plugin.yml" oder „keinen Executor" erscheinen.

## Zugriffsschutz
- Owner = UUID in `owner-uuids`. Diese bekommen beim Join automatisch die Permission `chunky.use`.
- Alternativ kann `chunky.use` per Permission-Plugin vergeben werden. Standard: niemand, auch keine OPs.
- Der Zugriff wird bei Ausführung **und** Tab-Vervollständigung geprüft. Alle Befehle haben in der `plugin.yml` die Permission `chunky.use`, daher sendet der Server die Befehle nur an berechtigte Clients. Ein Client kann trotzdem Befehle anderer Plugins/Vanilla sehen; das lässt sich per Plugin nicht vollständig verhindern.
- **UUID-Falle:** Online-Server nutzen Mojang-UUIDs, Server mit `online-mode=false` Offline-UUIDs (aus dem Namen berechnet). Dieselbe Person hat dort eine andere UUID. `/chunky uuid` zeigt die UUID, die der Server wirklich sieht (funktioniert für jeden Spieler).
- Wer abgewiesen wird, bekommt eine Meldung; die Konsole loggt Name und UUID. Mit `owner-name-hint` bekommt ein Spieler mit passendem Namen aber falscher UUID eine Warnung samt echter UUID (der Hint gibt keine Rechte).
- **Nebenwirkung:** `/give`, `/time`, `/tp`, `/spawn`, `/weather`, `/heal`, `/locate` usw. überschreiben die Vanilla-/Plugin-Versionen und sind danach nur noch für Owner und Konsole nutzbar.

## Befehle (alle nur für Owner/Konsole, außer `/chunky uuid`)
| Befehl | Beschreibung |
|---|---|
| `/chunky [uuid\|reload\|help]` | Eigene UUID anzeigen / Config neu laden |
| `/invsee <Spieler>` | Inventar eines **Online**-Spielers. Slots 0–35 Inventar (0–8 Hotbar), 36 Helm, 37 Brustplatte, 38 Hose, 39 Stiefel, 41 Offhand. Änderungen werden einen Tick nach dem Klick ins echte Inventar geschrieben, die Ansicht wird alle 10 Ticks aktualisiert. Shift-Klick/Doppelklick sind gesperrt (Schutz vor Itemverlust). |
| `/ecsee [Spieler]` | Endertruhe (live, Online-Spieler) |
| `/locate <Spieler> [tp]` | Welt, X/Y/Z, Entfernung (gleiche Welt); `tp` teleportiert dich hin |
| `/heal [Spieler]` | Gesundheit, Hunger, Sättigung auffüllen, Feuer löschen |
| `/feed [Spieler]` | Hunger auffüllen |
| `/give <Item> <Anzahl> [Spieler]` | Max. `limits.give-max-amount`. Was nicht ins Inventar passt, wird gedroppt (max. `give-max-dropped-stacks` Stacks); der Rest wird **nicht** gegeben und das wird gemeldet |
| `/army <Mob> <Anzahl> <Spieler>` | Spawnt Kreaturen um den Spieler und setzt ihr Ziel. Feindliche Mobs greifen an; passive Tiere (Schaf, Axolotl) haben keine Angriffs-KI gegen Spieler. Die Meldung nennt beide Zahlen |
| `/spawn <Mob> [Anzahl] [Spieler]` | Wie vereinfachtes `/summon`, nahe dir bzw. dem Spieler |
| `/bolt [Spieler]` | Echter Blitz (Schaden/Feuer möglich) am anvisierten Block oder beim Spieler |
| `/lightningstick` | Stock mit PDC-Marker; Rechtsklick = Blitz am anvisierten Block (nur für Berechtigte, normale Stöcke tun nichts) |
| `/missile <Spieler>` | Unsichtbarer Creeper ~6 Blöcke neben dem Spieler. Kann den Spieler später durchaus töten, wenn er explodiert. Wird nach `missile.lifetime-seconds` entfernt, beim Chunk-Laden/Start/Stop bereinigt. Explosion zerstört standardmäßig keine Blöcke |
| `/sphere <Block> <Radius>` | Hohle Kugel um dich (Limits siehe Config). Standardmäßig werden nur ersetzbare Blöcke (Luft, Wasser, Gras …) überschrieben; mit `sphere-only-replace-replaceable: false` wird **alles** überschrieben. Platzierung verteilt über mehrere Ticks |
| `/time [set] <day\|noon\|night\|midnight\|0-24000> [Welt]` | Weltzeit |
| `/weather <clear\|rain\|thunder> [Sekunden] [Welt]` | Wetter |
| `/vanish` | Für Nicht-Berechtigte unsichtbar (bis Logout/Neustart) |
| `/tp ...` | `<Spieler>`, `<Spieler> <Ziel>`, `<x> <y> <z>`, `<Spieler> <x> <y> <z>` (auch `~`) |
| `/creative` `/survival` `/spectator [Spieler]` | Spielmodus |
| `/god [Spieler]` | Schaden/Hunger aus (nicht persistent, nur im Speicher) |
| `/fly [Spieler]` | Flugmodus umschalten |
| `/speed <0-10\|reset> [Spieler]` | Geh- und Fluggeschwindigkeit |

Entfernt (nicht vorhanden): `/control`, `/noclip`, `/swapplayer`.

## Offene Punkte
- `/be`: Die alte JAR lag nicht vor, die Funktion von `/be` ist unbekannt – **nicht implementiert**. Bitte alte JAR hochladen oder die Funktion beschreiben.
- Das Verhalten anderer alter Befehle wurde nicht gegen die alte JAR verglichen (keine vorhanden), sie wurden neu implementiert.
- Alle Befehle für Offline-Spieler (invsee/ecsee/locate/heal …) sind nicht unterstützt; nur Online-Spieler.
- Folia wird nicht unterstützt (Bukkit-Scheduler).

## Tests
| Test | Ergebnis |
|---|---|
| Syntaxprüfung (javac Parse-Phase, ohne Paper-API) | Keine Syntaxfehler; 411 Folgefehler nur wegen fehlender Abhängigkeiten |
| Maven-Build mit Paper-API | **Nicht ausgeführt** (kein Maven/Netz) |
| Unit-Tests | **Keine vorhanden** |
| Start auf Paper 1.21.11 / Befehle live | **Nicht ausgeführt** |
| Prüfung JAR/Hauptklasse/`plugin.yml` in JAR | **Nicht möglich** (keine JAR) |
Die Startprüfung im Plugin meldet beim ersten Start, falls `plugin.yml` und Registrierung nicht übereinstimmen.
