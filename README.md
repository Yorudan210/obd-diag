# OBD Diag — Diagnostic OBD-II Android (ELM327 Bluetooth)

Application Android (Kotlin + Jetpack Compose) qui se connecte en Bluetooth
Classic (SPP) à un boîtier **ELM327**.

## Fonctions

**Véhicule**
- Version du boîtier, protocole détecté, tension batterie mesurée par le boîtier
- VIN, type de carburant, nom et référence logicielle du calculateur (mode 09)
- Voyant moteur (MIL), nombre de défauts, état des moniteurs d'autodiagnostic
  (dont FAP, EGR, suralimentation sur diesel)
- Liste des PIDs réellement déclarés par le calculateur

**Défauts**
- Lecture des codes stockés (mode 03), en attente (mode 07) et permanents (mode 0A)
- Image figée (mode 02) : valeurs au moment du défaut
- Effacement des défauts (mode 04) avec confirmation
- Dictionnaire de ~120 codes génériques SAE (injection, rampe, turbo, EGR, FAP, bougies)
- Export du rapport

**Temps réel** (uniquement les PIDs que le calculateur déclare)
- Moteur : régime, vitesse, charge, pédale, couple
- Injection : pression de rampe, calage, consommation
- Turbo / admission : MAP, débit d'air, pression turbo consigne/réelle, géométrie variable
- Antipollution : EGR, lambda, températures d'échappement, FAP (pression différentielle,
  températures) — *si le calculateur les expose en OBD-II standard*
- Températures : liquide, air, huile, ambiante
- Enregistrement des mesures et export CSV

**Modules (multi-marque)**
- Scan du bus CAN 11 bits : trouve tous les calculateurs qui répondent (moteur, boîte,
  ABS, airbag, carrosserie... selon le véhicule) sans connaître la marque à l'avance
- Lecture des défauts de chaque module : UDS (`19 02 FF`), repli automatique en KWP2000 (`18`)
- Identification via les identifiants standard ISO 14229 (`22 F1xx` : VIN, références
  pièce/logiciel/matériel, équipementier, série)
- Effacement des défauts par module (UDS `14`, repli KWP) avec confirmation
- Noms personnalisables par module, export du rapport complet

**Expert**
- Console ELM327 : requêtes brutes avec en-tête CAN (ATSH) et filtre (ATCRA)
- Lecture de défauts KWP2000 (`18 02 FF 00`) et UDS (`19 02 FF`) vers un module choisi
- Choix du protocole (ATSP), scan des calculateurs OBD-II
- **PIDs personnalisés** : requête + équation (style Torque), import CSV Torque,
  utilisables dans l'onglet Temps réel
- Confirmation obligatoire avant toute commande pouvant effacer/écrire/piloter

## Multi-marque : ce qui est (et n'est pas) couvert

L'onglet Modules marche sur tout véhicule dont les calculateurs suivent UDS / KWP2000 sur
CAN 11 bits, quelle que soit la marque. L'app n'a **pas** de table d'adresses par constructeur
(non fiable sans documentation) : elle les découvre, et l'utilisateur nomme les modules.
Non couvert : bus CAN 29 bits par module, K-line multi-modules (véhicules d'avant ~2008),
bus propriétaires (CCD/SCI Chrysler, J1850 par module), et toutes les fonctions
d'actionneurs, codage ou adaptation propres à une marque.

## Limites (à lire)

Le standard OBD-II ne couvre que les données « émissions ». Les fonctions
suivantes passent par des protocoles constructeur (PSA, VAG...) et **ne sont
pas intégrées** car leurs identifiants ne sont pas standardisés et une erreur
peut endommager un calculateur :

- taux de colmatage du FAP, masse de cendres, distance depuis régénération,
  niveau d'additif Eolys, régénération forcée
- corrections et apprentissage des injecteurs, codage
- ABS/ESP, airbags, BSI/BSM, climatisation, vitres, éclairage
- réinitialisation d'entretien, purge ABS, calibration angle volant

La console Expert et les PIDs personnalisés permettent d'interroger ces modules
si vous disposez des adresses et identifiants corrects pour votre moteur, mais
l'application ne les devine pas. Un boîtier ELM327 bon marché peut aussi
être trop limité pour certains modules (préférez un clone fiable).

## Prérequis

- Boîtier **ELM327 Bluetooth Classic** (pas BLE), appairé dans les réglages Android
  (PIN `1234` ou `0000`), branché sur la prise OBD, contact mis.
- Android 8.0+ (minSdk 26).

## Compilation

Chaque push sur `main` lance GitHub Actions : l'APK debug est publié comme
artefact `obddiag-apk`, et les tests unitaires du décodage sont exécutés.
En local : Android Studio (SDK 34), ouvrir le dossier puis Run.

## Structure

```
app/src/main/java/com/obddiag/app/
├── MainActivity.kt      # Navigation + écran de connexion
├── VehicleScreen.kt     # Infos véhicule, MIL, moniteurs
├── DtcScreen.kt         # Défauts, image figée, effacement, export
├── LiveScreen.kt        # Mesures temps réel, enregistrement CSV
├── ModulesScreen.kt     # Scan multi-marque, défauts/infos par module
├── ModuleLabelStore.kt  # Noms de modules personnalisés
├── ExpertScreen.kt      # Console, protocoles/scan, PIDs perso
├── ObdViewModel.kt      # État de l'app et orchestration
├── ObdSession.kt        # Logique OBD-II (modes 01/02/03/04/07/09/0A)
├── ObdConnection.kt     # Bluetooth SPP + commandes ELM327
├── ObdParser.kt         # Décodage des réponses (CAN multi-trames, DTC, VIN...)
├── PidCatalog.kt        # PIDs standard SAE J1979 et leurs formules
├── Formula.kt           # Évaluateur d'équations des PIDs perso
├── CustomPid.kt         # PID perso + import CSV Torque
├── CustomPidStore.kt    # Sauvegarde locale
├── DtcCodes.kt          # Dictionnaire des codes défauts
├── Exporter.kt          # Partage de fichiers
└── Common.kt            # Composants d'affichage communs
```
