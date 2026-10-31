# Spotibjork

Application Android qui affiche le lecteur web de Spotify (open.spotify.com) dans une WebView,
avec lecture en arrière-plan, notification média (écran de verrouillage) et boutons précédent /
lecture-pause / suivant.

- Identifiant : `com.spotibjork.app` (s'installe à côté de l'app Spotify du Play Store)
- Pas de bloqueur de pubs
- Aucune dépendance externe

## Compiler l'APK

1. Poste ce dossier sur un dépôt GitHub.
2. Onglet **Actions** > **Build Spotibjork** > **Run workflow**.
3. Télécharge l'APK dans les artefacts du run, puis installe-le.

## Réglages (app/src/main/kotlin/com/spotibjork/app/Config.kt)

- `LAYOUT_WIDTH_PX` : largeur de mise en page forcée (ex. 700) si l'affichage est trop serré.
- `USER_AGENT` : identité envoyée à Spotify.
