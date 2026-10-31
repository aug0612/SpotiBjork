# Spotibjork

Wrapper Android du lecteur web de Spotify (open.spotify.com), sans bloqueur de pubs.

- Identifiant `com.spotibjork.app` : s'installe à côté de l'app Spotify du Play Store.
- Lecture en arrière-plan (service de premier plan + verrou de réveil pendant la lecture).
- Notification média et contrôles de l'écran de verrouillage (précédent / lecture-pause / suivant).
- Aucune dépendance externe, aucun lien avec exhale ni mise à jour automatique.

## Compiler l'APK

1. Crée un dépôt GitHub et envoie-y le contenu de ce dossier (branche `main`).
2. Onglet **Actions** > **Build APK** > **Run workflow**.
3. Télécharge `Spotibjork-apk` dans les artefacts du run, dézippe, installe `app-debug.apk`.

## Limites connues

- Connexion Google / Facebook souvent refusée dans une WebView : utilise e-mail + mot de passe Spotify.
- Les contrôles média cliquent sur les boutons de la page (`data-testid` de Spotify) : si Spotify change sa page, ils peuvent cesser de marcher.
- Les infos de la notification viennent de `navigator.mediaSession` de la page.
- Pas de widgets ni d'Android Auto.
- Si tu glisses l'app hors des applications récentes, la lecture s'arrête.
