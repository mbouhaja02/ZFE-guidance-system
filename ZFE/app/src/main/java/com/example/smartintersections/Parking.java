package com.example.smartintersections;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;

public class Parking {

    public GeoPoint2D geo_point_2d;
    public String nom;
    public String adresse;
    public String etat;
    public int libres;       
    public float tarifHoraire; 
    public float transportScore;

     /**
     * Recherche le "meilleur" parking dans la liste (définie via loadParkingsFromJson ou autre),
     * en fonction d’un score calculé. 
     * @param userLoc        position actuelle de l’utilisateur
     * @param allParkings    liste de parkings à évaluer
     * @param parkRelaisOnly si true, on ne considère que ceux dont le nom commence par "Parc-Relais"
     * @param w1..w4         pondérations pour la formule de score
     * @return               l’objet Parking optimal, ou null si aucun
     */
    public static Parking findBestParking(
            LatLng userLoc,
            List<Parking> allParkings,
            boolean parkRelaisOnly,
            float w1, float w2, float w3, float w4
    ) {
        if (allParkings == null || allParkings.isEmpty()) {
            return null;
        }

        Parking best = null;
        float bestScore = -9999f;

        for (Parking p : allParkings) {
            if (p.nom == null || p.geo_point_2d == null) {
                continue;
            }

            // Filtre Parc-Relais si nécessaire
            if (parkRelaisOnly && !p.nom.startsWith("Parc-Relais")) {
                continue;
            }

            // Calcul de la distance
            float distance = computeDistance(
                    userLoc,
                    new LatLng(p.geo_point_2d.lat, p.geo_point_2d.lon)
            );

            // On suppose que p.libres, p.tarifHoraire, p.transportScore sont remplis
            int places    = p.libres;
            float price   = p.tarifHoraire;
            float tScore  = p.transportScore;

            float score   = computeScore(distance, places, price, tScore, w1, w2, w3, w4);

            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }


    public static float computeDistance(LatLng latLng1, LatLng latLng2) {
        float[] results = new float[1];
        android.location.Location.distanceBetween(
                latLng1.latitude, latLng1.longitude,
                latLng2.latitude, latLng2.longitude,
                results);
        // results[0] est la distance en mètres
        return results[0] / 1000f; // en km
    }


    /**
     * Exemple de méthode pour calculer le score final d'un parking
     * en fonction de la distance, du tarif, etc.
     *
     * @param distance       Distance du parking (en km, par ex.)
     * @param freePlaces     Nombre de places libres
     * @param pricePerHour   Tarif horaire
     * @param transportScore Score d'accès aux transports (0..1) ou un indicateur...
     * @param w1
     * @param w2
     * @param w3
     * @param w4
     * @return Le score calculé selon w1..w4
     */
    public static float computeScore(float distance, int freePlaces, float pricePerHour, float transportScore, float w1, float w2, float w3, float w4) {
        // Score total = w1×Distance^-1 + w2×Places libres + w3×Tarif^-1 + w4×T
        // distance^-1 = 1/distance (faites attention à distance=0)
        float invDistance = (distance > 0) ? (1f / distance) : 9999;  // Eviter division par 0
        float invPrice    = (pricePerHour > 0) ? (1f / pricePerHour) : 9999;

        // On suppose transportScore = T déjà calculé (0..1) ou un indice plus grand
        // A ajuster selon votre logique
        float result = (w1 * invDistance)
                     + (w2 * freePlaces)
                     + (w3 * invPrice)
                     + (w4 * transportScore);
        return result;
    }



}
