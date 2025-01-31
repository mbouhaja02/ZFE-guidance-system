package com.example.smartintersections;

import android.graphics.Color;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.PolyUtil;

import java.util.List;

/**
 * Classe pour gérer la logique ZFE : 
 * - vérifier si l'utilisateur est dans la ZFE,
 * - alerter l'utilisateur,
 * - etc.
 */
public class ZfeHelper {

    // Référence à votre MainActivity pour accéder à l'UI, 
    // ou au moins un Context pour les Toast, etc.
    private final MainActivity activity;
    private final List<LatLng> zfePolygon;  // la liste de points qui définit la ZFE

    public ZfeHelper(MainActivity activity, List<LatLng> zfePolygon) {
        this.activity = activity;
        this.zfePolygon = zfePolygon;
    }

    /**
     * Met à jour l'alerte ZFE pour la position userLocation.
     */
    public void updateZfeAlert(LatLng userLocation) {
        if (zfePolygon == null || zfePolygon.isEmpty()) {
            // Sécurité : si le polygone n'est pas chargé
            Log.i("ZFE", "Polygon non chargé ou vide");
            showZfeAlert("Alerte ZFE : impossible de déterminer la zone !");
            return;
        }
        if (userLocation == null) {
            // Si on n'a pas la position, on ne peut rien faire
            return;
        }

        boolean inside = PolyUtil.containsLocation(userLocation, zfePolygon, false);
        if (inside) {
            showZfeAlert("Alerte ZFE : vous êtes dans la zone !");
        } else {
            // Calcul de la distance la plus proche du polygone
            double minDistance = calcMinDistance(userLocation, zfePolygon);
            // Exemple : on met 200 mètres comme seuil
            if (minDistance < 200.0) {
                showZfeAlert("Alerte ZFE : vous êtes à < 200 m !");
            } else {
                showZfeOk("Situation ZFE : Aucun risque");
            }
        }
    }

    /**
     * Calcule la distance minimale entre un point (userLocation) et un polygone (zfePolygon).
     */
    private double calcMinDistance(LatLng userLocation, List<LatLng> polygon) {
        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < polygon.size(); i++) {
            LatLng start = polygon.get(i);
            LatLng end = polygon.get((i + 1) % polygon.size());
            double dist = PolyUtil.distanceToLine(userLocation, start, end);
            if (dist < minDist) {
                minDist = dist;
            }
        }
        return minDist;
    }

    /**
     * Affiche un message d'alerte ZFE en rouge.
     */
    private void showZfeAlert(String message) {
        activity.runOnUiThread(() -> {
            TextView zfeAlertText = activity.findViewById(R.id.zfeAlertText);
            zfeAlertText.setText(message);
            zfeAlertText.setTextColor(Color.RED);
        });
    }

    /**
     * Affiche un message d'alerte 'ok' (non bloquant) en vert.
     */
    private void showZfeOk(String message) {
        activity.runOnUiThread(() -> {
            TextView zfeAlertText = activity.findViewById(R.id.zfeAlertText);
            zfeAlertText.setText(message);
            zfeAlertText.setTextColor(Color.GREEN);
        });
    }

}
