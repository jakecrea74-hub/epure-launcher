# Épure — launcher Android TV

Accueil épuré : fond sombre, message d'accueil, heure et météo en haut, une rangée d'applis en bas.
Appli native (Java + Android pur, aucune bibliothèque).

## Mise en place (une seule fois)

1. Sur github.com, créer un dépôt **public** nommé `epure-launcher` (public = la TV peut lire les réglages et les mises à jour sans mot de passe).
2. Dans le dépôt : Settings → Secrets and variables → Actions → New repository secret. Créer les 2 secrets du fichier `epure-secrets-github.txt` : `KEYSTORE_PASSWORD` et `KEYSTORE_BASE64`.
3. Onglet Code → Add file → Upload files : glisser tout le contenu du dossier `epure-launcher`. Sur Mac, le dossier `.github` est caché : dans le Finder, faire Cmd + Maj + . pour l'afficher.
4. Onglet Actions : attendre la coche verte (3 à 5 min). La version apparaît dans Releases.
5. Sur la TV, appli Downloader, saisir :
   `https://github.com/VOTRE-COMPTE/epure-launcher/releases/latest/download/epure.apk`
   puis Installer.
6. Appuyer sur le bouton Accueil de la télécommande et choisir Épure → « Toujours ».

## Modifier les réglages

Éditer `settings.json` directement sur GitHub (icône crayon). La TV le relit au démarrage et en revenant à l'accueil (délai possible de quelques minutes, cache GitHub).

| Clé | Rôle |
|---|---|
| `prenom` | Ajouté au message (« Bonsoir, Jake ») |
| `message_accueil` | Remplace le message automatique si rempli |
| `ville`, `latitude`, `longitude` | Météo |
| `afficher_meteo`, `format_24h` | true / false |
| `animations` | true / false (zoom et fondu) |
| `couleur_fond_haut`, `couleur_fond_bas` | Dégradé du fond |
| `couleur_ambiance_1`, `couleur_ambiance_2` | Couleurs du fond animé |
| `sous_titres` | Petit texte sous le nom d'une appli |
| `ordre_applis` | Applis à placer en premier (noms de paquet) |
| `applis_masquees` | Applis à cacher (noms de paquet) |
| `inclure_applis_non_tv` | Afficher aussi les applis non prévues pour TV |
| `maj_auto` | Proposer les nouvelles versions automatiquement |

Modifier `settings.json` ne recompile rien.

## Mises à jour

Chaque modification du code sur GitHub crée une nouvelle version. La TV la propose au retour à l'accueil (vérification toutes les 6 h), ou via la tuile Réglages → Rechercher une mise à jour.
La première fois, Android demande d'autoriser Épure à installer des applis.

## Télécommande

- Gauche / droite : naviguer · OK : ouvrir
- Tuile ⚙ (fin de rangée) ou touche Menu : réglages (animations, mise à jour, recharger, paramètres TV)
