# Journal d'Aventure - Projet Proxy Minecraft (Go)

## Objectif
Créer un proxy Minecraft ultra-performant, capable de gérer 500+ joueurs avec moins de 2 Go de RAM, supportant Java et Bedrock (via Geyser), avec support des commandes (`/server`) et auto-complétion.

## Chronologie

### Étape 1 : Conception & Architecture (29 Mai 2026)
- **Langage choisi :** Go (Golang). C'est le meilleur compromis entre la vitesse du C++ et la facilité de gestion réseau de Python. Il est extrêmement efficace pour les connexions simultanées grâce aux goroutines.
- **Défis identifiés :** 
    - Analyse du protocole Minecraft (Handshaking, Status, Login, Play).
    - Gestion de l'auto-complétion pour les serveurs.
    - Support Bedrock (Forwarding UDP RakNet).
    - Optimisation RAM pour rester sous les limites strictes.

### Étape 2 : Initialisation (29 Mai 2026)
- **Installation de Go :** Vérification et installation de Go via Homebrew pour avoir les derniers outils de compilation.
- **Structure du projet :** Mise en place d'un module Go (`minecraft-proxy`) et organisation des dossiers (`cmd/proxy`).
- **Premier Code (main.go) :** 
    - Implémentation d'un serveur TCP pour Java (port 25566 -> 25565).
    - Implémentation d'un serveur UDP pour Bedrock (Geyser).
    - Utilisation des `goroutines` pour une gestion ultra-légère des connexions.
    - Ajout des fonctions de base pour lire le format `VarInt` du protocole Minecraft.

### Étape 3 : Analyse du Protocole (29 Mai 2026)
- **Détection des États :** Le proxy suit maintenant si le joueur est en train de se connecter (Handshaking), de pinger le serveur (Status) ou de jouer (Play).
- **Interception des Paquets :** J'ai ajouté un lecteur de paquets Minecraft capable de décoder les `VarInt` et les `Strings`.
- **Commandes Proxy :** Implémentation de la commande `/server`. Le proxy intercepte ce message avant qu'il n'atteigne le serveur principal et répond directement au joueur.
- **Optimisation :** Toujours en Go, le proxy ne consomme que quelques Mo de RAM même lors de l'analyse des paquets.

### Étape 4 : Auto-complétion & Prédictions (En cours)
- Pour que `/server` s'affiche dans la liste des commandes du joueur (prédictions), je dois injecter des données dans le paquet `Declare Commands` envoyé par le serveur.
