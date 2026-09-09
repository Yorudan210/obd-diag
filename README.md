# OBD Diag — App Android de diagnostic OBD-II (ELM327)

Application Android (Kotlin + Jetpack Compose) qui se connecte en Bluetooth
Classic (SPP) à un boîtier ELM327 pour :

- **Lire les codes défauts** génériques stockés (Mode 03 OBD-II)
- **Effacer les codes défauts** (Mode 04 OBD-II)
- **Afficher des données temps réel** : régime moteur, vitesse, température
  liquide de refroidissement, position papillon, charge moteur, niveau de
  carburant, tension batterie, température air admission.

## Portée et limites (important)

Cette application couvre le diagnostic **OBD-II générique standardisé**
(SAE J1979), compatible avec **tous les véhicules essence/diesel** depuis
le milieu des années 2000 environ.

Elle **ne fait pas** :
- de codage/programmation de modules constructeur (nécessite un protocole
  propriétaire + accès sécurisé + interface J2534 ou outil constructeur) ;
- de lecture des codes défauts *spécifiques constructeur* (souvent en dehors
  de la plage P0xxx/P1xxx génériques) ;
- de tests d'actionneurs avancés.

C'est une base solide et fonctionnelle pour du diagnostic générique —
l'extension vers du multi-marque avancé demanderait une interface J2534 et
les spécifications protocolaires de chaque constructeur (souvent payantes
et sous licence).

## Prérequis matériel

- Un boîtier **ELM327 Bluetooth Classic** (pas BLE) — les clones à ~10€
  fonctionnent généralement bien pour l'OBD-II générique.
- **Appairer le boîtier au préalable** dans les réglages Bluetooth d'Android
  (PIN généralement `1234` ou `0000`), avant d'ouvrir l'app.

## Compiler le projet

1. Ouvrir le dossier dans **Android Studio** (version récente, avec le SDK 34).
2. Laisser Gradle synchroniser (télécharge les dépendances au premier build).
3. Brancher un téléphone Android (minSdk 26, soit Android 8.0+) ou lancer un
   émulateur avec support Bluetooth (limité en émulateur — un vrai
   téléphone est recommandé).
4. Lancer l'app (Run ▶).

## Structure du projet

```
app/src/main/java/com/obddiag/app/
├── MainActivity.kt      # UI Compose (connexion, DTC, temps réel)
├── ObdViewModel.kt       # État de l'app, orchestration des appels
├── ObdConnection.kt      # Connexion Bluetooth SPP + envoi commandes AT/OBD
├── DtcCodes.kt           # Décodage des DTC (Mode 03) + dictionnaire de descriptions
└── PidParser.kt          # Décodage des PIDs Mode 01 pour les données temps réel
```

## Pistes d'évolution

- Ajouter plus de PIDs (pression carburant, avance à l'allumage, débit MAF...).
- Enrichir le dictionnaire de codes DTC (actuellement ~35 codes courants).
- Ajouter un export PDF/CSV du diagnostic.
- Ajouter le mode 02 (freeze frame) pour contextualiser les codes défauts.
- Historique des connexions/scans dans une base locale (Room).
