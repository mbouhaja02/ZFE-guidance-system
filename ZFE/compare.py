#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import math

################################################################################
# 1) DONNÉES FACTICES
################################################################################

# Exemple : liste de parkings (fictive)
#  - distance : en km
#  - free : nombre de places libres
#  - price : tarif horaire en euros
#  - transport : un score entre 0 et 1 (accès transports en commun, etc.)

parkings = [
    {"name": "Parking A", "distance": 1.5, "free": 20, "price": 2.0, "transport": 0.8},
    {"name": "Parking B", "distance": 0.2, "free": 5,  "price": 3.0, "transport": 0.9},
    {"name": "Parking C", "distance": 3.0, "free": 50, "price": 1.0, "transport": 0.4},
    {"name": "Parking D", "distance": 10.0,"free": 200,"price": 2.5,"transport": 0.6},
    {"name": "Parking E", "distance": 0.5, "free": 10, "price": 5.0, "transport": 1.0}
]

# Pondérations (à normaliser si besoin)
w1, w2, w3, w4 = 0.25, 0.25, 0.25, 0.25

################################################################################
# 2) FONCTIONS DE SCORING (FORMULES)
################################################################################

# ======================
# Formule 1 : Linéaire
# ======================
def score_lineaire(distance, free, price, transport, w1, w2, w3, w4):
    # distance^-1
    inv_dist = 1.0 / distance if distance > 0 else 9999
    # price^-1
    inv_price = 1.0 / price if price > 0 else 9999
    
    score = (w1 * inv_dist) \
          + (w2 * free)      \
          + (w3 * inv_price) \
          + (w4 * transport)
    return score


# ===========================
# Formule 2 : Multiplicative
# ===========================
def score_multiplicatif(distance, free, price, transport, w1, w2, w3, w4,
                        dist_max=10.0, free_max=200.0, price_max=5.0):
    """
    Exemple : on normalise distance, free, price, transport entre [0..1],
    puis on fait un produit avec des exposants.
    
    distance_norm   = 1 - (distance / dist_max)  (pour que 0 km => 1,  dist_max => 0)
    free_norm       = free / free_max            (0 => 0,  free_max => 1)
    price_norm      = price / price_max          (0 => 0,  price_max => 1)
    transport_norm  = transport (déjà entre 0..1)
    
    On élève ensuite ces termes à +/- w_i selon l’effet désiré.
    """
    # Normalisations (on peut saturer si > dist_max, etc.)
    d = min(distance, dist_max)       # saturer
    f = min(free, free_max)
    p = min(price, price_max)
    
    distance_norm  = 1.0 - (d / dist_max)       # => 0..1 (1 = meilleur)
    free_norm      = f / free_max               # => 0..1 (1 = meilleur)
    price_norm     = p / price_max              # => 0..1 (1 = plus cher)
    transport_norm = transport                  # supposé déjà 0..1
    
    # On veut encourager distance faible => on utilise distance_norm^(+w1)
    # On veut encourager free élevé => free_norm^(+w2)
    # On veut encourager un tarif bas => on peut faire (1 - price_norm)^(+w3) 
    #   ou price_norm^(-w3), etc. Selon la logique souhaitée.
    
    # Ex ci-dessous : le paramètre w3 est appliqué négativement
    # => plus le prix_norm est grand, plus le terme est faible.
    # => on veut punir un tarif élevé.
    if price_norm == 0:
        # si tarif = 0 => parking gratuit => c’est le top !
        price_factor = 1.0
    else:
        price_factor = (price_norm) ** (-w3)
    
    score = (distance_norm ** w1) \
          * (free_norm ** w2)     \
          * price_factor          \
          * (transport_norm ** w4)
    
    return score


# ===========================
# Formule 3 : Exponentielle
# ===========================
def score_exponentiel(distance, free, price, transport, w1, w2, w3, w4,
                      alpha_dist=0.5, alpha_price=1.0, beta=0.5, gamma=1.0):
    """
    Exemple : score = w1 * e^(-alpha_dist * distance)
                    + w2 * free^beta
                    + w3 * e^(-alpha_price * price)
                    + w4 * (transport^gamma)
    """
    term_dist  = math.exp(-alpha_dist * distance)     # e^(-alpha * distance)
    term_free  = free**beta                           # places^beta
    term_price = math.exp(-alpha_price * price)       # e^(-alpha * price)
    term_trans = transport**gamma                     # transport^gamma
    
    score = (w1 * term_dist) \
          + (w2 * term_free) \
          + (w3 * term_price) \
          + (w4 * term_trans)
    return score


# ======================================
# Formule 4 : Distance à un "optimum"
# ======================================
def score_distance_optimum(distance, free, price, transport, 
                           w1, w2, w3, w4,
                           dist_max=10.0, free_max=200, price_max=5.0):
    """
    On définit un "optimum" (distance=0, free=free_max, price=0, transport=1).
    On normalise chaque critère, puis on calcule la distance euclidienne
    pondérée par w1..w4. Ensuite, on convertit cette distance en un score,
    par exemple score = exp(-D).
    """
    # Normalisations sur [0..1]
    distance_norm  = min(distance, dist_max) / dist_max          # (0 => parfait, 1 => max)
    free_norm      = 1.0 - (min(free, free_max) / free_max)      # on veut 0 => 1 si free=free_max
    price_norm     = min(price, price_max) / price_max           # 0 => parking gratuit
    transport_norm = transport                                   # déjà 0..1
    
    # L’optimum => (distance_norm=0, free_norm=1, price_norm=0, transport_norm=1).
    # On calcule la distance dans un espace 4D, pondéré par w_i :
    # d^2 = w1 * (distance_norm - 0)^2
    #      + w2 * (free_norm - 1)^2
    #      + w3 * (price_norm - 0)^2
    #      + w4 * (transport_norm - 1)^2
    
    # ATTENTION : ici, w2 correspond à free => si w2=0.25, on pèse (free_norm-1)^2 par 0.25
    
    d2 = w1*(distance_norm - 0)**2      \
       + w2*(free_norm - 1)**2         \
       + w3*(price_norm - 0)**2        \
       + w4*(transport_norm - 1)**2
    
    d = math.sqrt(d2)
    
    # On transforme la distance en score
    # plus d est petit => plus le score est grand.
    score = math.exp(-d)  # entre 0 et 1 environ
    return score


# ===========================
# Formule 5 : Pareto
# ===========================
def pareto_front(parkings):
    """
    Approche multi-critères Pareto :
    - On ne calcule pas de score unique.
    - On cherche l’ensemble des parkings "non dominés".
    Critères de domination :
       A domine B si:
         A.distance <= B.distance, A.price <= B.price, A.free >= B.free, A.transport >= B.transport
       et au moins une inégalité stricte.
    On peut évidemment affiner les conditions (plus de critères, etc.).
    """
    
    # Conversion sous forme de tuples (distance, -free, price, -transport) pour uniformiser
    # l’idée : on veut min(distance), max(free), min(price), max(transport).
    # => On va dire qu’un plus petit "vecteur" domine un plus grand vecteur.
    #    d’où le fait qu’on prend -free et -transport pour inverser le sens.
    
    def is_dominated(pA, pB):
        """
        Retourne True si pA est dominé par pB (i.e. pB meilleur ou égal partout, 
        et strictement meilleur sur au moins 1 critère).
        """
        # pA, pB : dictionnaires { "distance", "free", "price", "transport" }
        # Condition : 
        #    pB.distance <= pA.distance
        #    pB.free >= pA.free
        #    pB.price <= pA.price
        #    pB.transport >= pA.transport
        # + au moins 1 strict
        worse_or_equal = (
            pB["distance"] <= pA["distance"] and
            pB["free"]    >= pA["free"] and
            pB["price"]   <= pA["price"] and
            pB["transport"] >= pA["transport"]
        )
        if not worse_or_equal:
            return False
        
        # Vérif stricte sur au moins 1
        strict = (
            (pB["distance"] < pA["distance"]) or
            (pB["free"]    > pA["free"]) or
            (pB["price"]   < pA["price"]) or
            (pB["transport"] > pA["transport"])
        )
        return strict
    
    non_dominated = []
    
    for i, pA in enumerate(parkings):
        dominated = False
        for j, pB in enumerate(parkings):
            if i != j:
                if is_dominated(pA, pB):
                    dominated = True
                    break
        if not dominated:
            non_dominated.append(pA)
    
    return non_dominated


################################################################################
# 3) EXÉCUTION DE L’EXEMPLE
################################################################################

if __name__ == "__main__":
    print("Calcul des scores pour chaque parking, selon les 4 premières formules...\n")
    
    # Pour chaque parking, on calcule 4 scores
    for p in parkings:
        dist    = p["distance"]
        free    = p["free"]
        price   = p["price"]
        trans   = p["transport"]
        
        # 1) Linéaire
        sc_lin = score_lineaire(dist, free, price, trans, w1, w2, w3, w4)
        
        # 2) Multiplicatif
        sc_mult = score_multiplicatif(dist, free, price, trans, w1, w2, w3, w4,
                                      dist_max=10.0, free_max=200.0, price_max=5.0)
        
        # 3) Exponentiel
        sc_exp = score_exponentiel(dist, free, price, trans, w1, w2, w3, w4,
                                   alpha_dist=0.5, alpha_price=1.0,
                                   beta=0.5, gamma=1.0)
        
        # 4) Distance à l’optimum
        sc_opt = score_distance_optimum(dist, free, price, trans,
                                        w1, w2, w3, w4,
                                        dist_max=10.0, free_max=200.0, price_max=5.0)
        
        print(f"Parking {p['name']}:")
        print(f"  - Formule Linéaire     = {sc_lin:.4f}")
        print(f"  - Formule Multiplicat. = {sc_mult:.4f}")
        print(f"  - Formule Exponentiel  = {sc_exp:.4f}")
        print(f"  - Distance Optimum     = {sc_opt:.4f}")
        print()
    
    # 5) Pareto
    print("Analyse Pareto (pas de score unique) :")
    front = pareto_front(parkings)
    print("Parkings non dominés = ", [p["name"] for p in front])
