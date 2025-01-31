package com.example.smartintersections;

import com.example.smartintersections.Parking;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.android.libraries.places.api.model.Place;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;


import androidx.cardview.widget.CardView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.PolyUtil;

import android.Manifest;


import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;


import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.MarkerOptions;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Affiche une carte Google Maps, centrée sur Bordeaux,
 * et dessine un polygone correspondant à la ZFE définie
 * dans le fichier coordonnees.json (format {name, description, coordinates[]}).
 */
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;

    private ZfeData zfeData;
    private Button btnGoToMyLocation;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 101;


    private TextView statusTextView;



    private FusedLocationProviderClient fusedLocationClient;
    private FusedLocationProviderClient fusedLocationProviderClient;

    // Variables globales pour stocker les pondérations
    private float w1 = 0.25f; // Distance^-1
    private float w2 = 0.25f; // Places libres
    private float w3 = 0.25f; // Tarif^-1
    private float w4 = 0.25f; // Accès transports

    private boolean parkRelaisOnly = false;

    // EXEMPLE : On stocke la "loc" courante (latitude/longitude) ici
    private LatLng currentUserLocation = null;

    /**
     * Modèle pour représenter les coordonnées lat/lng.
     */
    private static class ZfeCoordinate {
        double lat;
        double lng;
    }

    /**
     * Modèle pour représenter la structure globale du JSON :
     * {
     *   "name": "Zone Faible Émission Bordeaux",
     *   "description": "Délimitation de la ZFE sur Bordeaux",
     *   "coordinates": [ {lat, lng}, ... ]
     * }
     */
    private static class ZfeData {
        String name;
        String description;
        List<ZfeCoordinate> coordinates;
    }

    private SpeedometerView speedometerView;
    private Handler speedUpdateHandler = new Handler();
    private Runnable speedUpdateRunnable;
    private static final String SPEED_FILE_NAME = "vitesse.txt";


    private List<LatLng> zfePolygon = new ArrayList<>();
    private CardView zfeAlertCard;
    private TextView zfeAlertText;



    private LatLng originLatLng = null;
    private LatLng destLatLng = null;

    private boolean isChoosingOrigin = false;
    private boolean isChoosingDest = false;


    private Handler handler = new Handler();
    private Runnable updateZfeTask;
    

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult locationResult) {
            super.onLocationResult(locationResult);
            Location loc = locationResult.getLastLocation();
            if (loc != null) {
                LatLng userPos = new LatLng(loc.getLatitude(), loc.getLongitude());
                currentUserLocation = userPos;
                updateZfeAlert(userPos);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Appelez cette méthode dans onCreate après setContentView()
        createSpeedFileIfNotExists();
        goToMyLocation();

        zfeData = loadZfeDataFromJson("zone_faible_emission_bordeaux.json");

        if (zfeData != null && zfeData.coordinates != null) {
            for (ZfeCoordinate c : zfeData.coordinates) {
                // Remplir la liste zfePolygon (globale)
                zfePolygon.add(new LatLng(c.lat, c.lng));
            }
        }

        // Liez le TextView pour afficher les alertes
        TextView zfeAlertText = findViewById(R.id.zfeAlertText);

        // Initialiser le SpeedometerView
        speedometerView = findViewById(R.id.speedometerView);
        speedometerView.setMaxSpeed(120f); // Définir la vitesse maximale si nécessaire

        // Configurer le Runnable pour mettre à jour la vitesse
        speedUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                // Lire la vitesse depuis le fichier
                float speed = readSpeedFromFile();
                if (speed >= 0) { // Vérifier si la vitesse est valide
                    speedometerView.setSpeed(speed);
                }
                // Reprogrammer la tâche après 1 seconde
                speedUpdateHandler.postDelayed(this, 1000);
            }
        };

        // Démarrer la mise à jour de la vitesse
        speedUpdateHandler.post(speedUpdateRunnable);


        


        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Démarrer les mises à jour de localisation si la permission est accordée
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }

        // Récupération du fragment de la carte
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        Places.initialize(getApplicationContext(), "AIzaSyB_Hw9UXtGSSmfxKQyd9c-BUJLX8p7uW_4");
        PlacesClient placesClient = Places.createClient(this);

        // 3) Récupérer le AutocompleteSupportFragment (Départ)
        AutocompleteSupportFragment originFragment = (AutocompleteSupportFragment)
        getSupportFragmentManager().findFragmentById(R.id.autocomplete_origin);

        if (originFragment != null) {
            originFragment.setPlaceFields(Arrays.asList(
                    Place.Field.ID, 
                    Place.Field.NAME, 
                    Place.Field.LAT_LNG));
            originFragment.setHint("Départ");

            originFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
                @Override
                public void onPlaceSelected(@NonNull Place place) {
                    originLatLng = place.getLatLng();
                    Toast.makeText(MainActivity.this, 
                        "Départ : " + place.getName(), 
                        Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(@NonNull Status status) {
                    Log.e("MainActivity", "Erreur Autocomplete Origin: " + status);
                }
            });
        }

        Button btnUseMyLocation = findViewById(R.id.btnUseMyLocation);
        btnUseMyLocation.setOnClickListener(view -> {
            // Méthode pour définir l'origine = position GPS
            setOriginFromGPS();
        });



        // 4) Récupérer le AutocompleteSupportFragment (Arrivée)
        AutocompleteSupportFragment destFragment = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.autocomplete_destination);

        destFragment.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG));
        destFragment.setHint("Arrivée");

        destFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(@NonNull Place place) {
                destLatLng = place.getLatLng();
                Toast.makeText(MainActivity.this, 
                    "Arrivée : " + place.getName(), 
                    Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull com.google.android.gms.common.api.Status status) {
                Log.e("MainActivity", "Erreur Autocomplete Dest: " + status);
            }
        });

        // 5) Bouton "Itinéraire"
        Button btnGetDirections = findViewById(R.id.btnGetDirections);
        btnGetDirections.setOnClickListener(view -> {
            if (originLatLng == null || destLatLng == null) {
                Toast.makeText(MainActivity.this, 
                    "Veuillez choisir Départ et Arrivée", 
                    Toast.LENGTH_SHORT).show();
                Log.i("Directions22", "No routes found");
                return;
            }
            fetchDirections(originLatLng, destLatLng, "driving");
        });



        // 4) Vérifier/demander la permission de localisation
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
        
        btnGoToMyLocation = findViewById(R.id.btnGoToMyLocation);
        btnGoToMyLocation.setOnClickListener(view -> {
            goToMyLocation();
        });

        // Demande la permission si pas déjà accordée
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE
            );
        }

        // Récupère les boutons
        Button btnZoomIn = findViewById(R.id.btnZoomIn);
        Button btnZoomOut = findViewById(R.id.btnZoomOut);

        // Gère le clic sur Zoom In
        btnZoomIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mMap != null) {
                    mMap.animateCamera(CameraUpdateFactory.zoomIn());
                }
            }
        });

        // Gère le clic sur Zoom Out
        btnZoomOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mMap != null) {
                    mMap.animateCamera(CameraUpdateFactory.zoomOut());
                }
            }
        });

        Button btnPreferences = findViewById(R.id.btnPreferences); 
        btnPreferences.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Ouvrir la boîte de dialogue
                showPreferencesDialog();
            }
        });

        statusTextView = findViewById(R.id.statusTextView);


        CardView zfeAlertCard = findViewById(R.id.zfeAlertCard);
        zfeAlertText = findViewById(R.id.zfeAlertText);


        Log.i("MainActivity", "inside ? ");
        // -----------------
        // BOUTON PARKINGS
        // -----------------
        Button btnParkings = findViewById(R.id.btnParkings);
        btnParkings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mMap == null) return;

                // Boîte de dialogue avec 3 choix
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Afficher les parkings ?");
                
                // Les 3 options
                String[] options = {"Tous les parkings", "Parkings Relais", "Annuler"};
                
                builder.setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0: // Tous les parkings
                                afficherParkings(false);
                                break;
                            case 1: // Parkings Relais (nom commence par "Parc-Relais")
                                afficherParkings(true);
                                break;
                            case 2: // Annuler - ne rien afficher
                                mMap.clear();
                                drawZfe(); // On réaffiche la ZFE
                                break;
                        }
                        drawZfe();
                    }
                });
                

                builder.show();
            }
        });



        Button btnFindParking = findViewById(R.id.btnFindParking);
        btnFindParking.setOnClickListener(v -> {
            if (mMap == null) return;
            if (currentUserLocation == null) {
                Toast.makeText(this, "Localisation inconnue !", Toast.LENGTH_SHORT).show();
                return;
            }
            // Chercher le parking optimal
            Parking best = findBestParking(currentUserLocation);
            if (best != null) {
                // Affiche un marker sur le parking
                LatLng pLatLng = new LatLng(best.geo_point_2d.lat, best.geo_point_2d.lon);
                mMap.addMarker(new MarkerOptions()
                        .position(pLatLng)
                        .title(best.nom));
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pLatLng, 13f));
                Toast.makeText(this, "Meilleur parking trouvé : " + best.nom, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Aucun parking trouvé", Toast.LENGTH_SHORT).show();
            }
        });

        // Optionnel : activer la localisation (voir la méthode ci-dessous)
        enableUserLocation();

        updateZfeTask = new Runnable() {
            @Override
            public void run() {
                // Appelez la fonction updateZfeAlert avec la localisation actuelle
                updateZfeAlert(currentUserLocation);
    
                // Relancez cette tâche après 1 seconde
                handler.postDelayed(this, 1000);
            }
        };
    
        // Démarrez la tâche répétée
        handler.post(updateZfeTask);
        
    }

    /**
     * Méthode pour activer la localisation (exemple simplifié).
     * Il faut ajouter la permission "ACCESS_FINE_LOCATION" dans le Manifest
     * et gérer la demande de permission à l'exécution pour Android 6+.
     */
    private void enableUserLocation() {
        if (mMap != null) {
            try {
                // Vérifier/demander la permission dans un vrai projet
                mMap.setMyLocationEnabled(true);

                // Optionnel : on peut essayer d'obtenir la loc courante
                // via FusedLocationProviderClient ou un autre API 
                // (ici, on fait un exemple simplifié).
                // Supposons qu'on obtienne lat/lon => on stocke dans currentUserLocation.
                // ...
                // currentUserLocation = new LatLng(..., ...);

                mMap.getUiSettings().setMyLocationButtonEnabled(false); 

            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
    }

    private void showPreferencesDialog() {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_preference, null);
    
        // 1) Récupérer la CheckBox
        CheckBox checkBoxParkRelais = dialogView.findViewById(R.id.checkBoxParkRelais);
    
        // 2) Récupérer les SeekBars
        SeekBar seekW1 = dialogView.findViewById(R.id.seekBarW1);
        SeekBar seekW2 = dialogView.findViewById(R.id.seekBarW2);
        SeekBar seekW3 = dialogView.findViewById(R.id.seekBarW3);
        SeekBar seekW4 = dialogView.findViewById(R.id.seekBarW4);
    
        // 3) Initialiser CheckBox et SeekBars
        checkBoxParkRelais.setChecked(parkRelaisOnly);
    
        // Convertir les w1..w4 (0..1) en pourcentage (0..100)
        seekW1.setProgress((int) (w1 * 100));
        seekW2.setProgress((int) (w2 * 100));
        seekW3.setProgress((int) (w3 * 100));
        seekW4.setProgress((int) (w4 * 100));
    
        // 4) Pour gérer la "somme = 100", on crée un écouteur partagé
        SeekBar.OnSeekBarChangeListener seekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return; 
                // Ajuster toutes les barres pour que la somme reste 100
                // après le changement de l’utilisateur.
    
                // 1) Récupérer valeurs actuelles
                int p1 = seekW1.getProgress();
                int p2 = seekW2.getProgress();
                int p3 = seekW3.getProgress();
                int p4 = seekW4.getProgress();
    
                // 2) Calculer la somme
                int sum = p1 + p2 + p3 + p4;
                if (sum == 0) {
                    // Cas extrême: toutes à 0 => on force un état neutre (25..)
                    seekW1.setProgress(25);
                    seekW2.setProgress(25);
                    seekW3.setProgress(25);
                    seekW4.setProgress(25);
                    return;
                }
    
                // 3) Si somme != 100, on rescale
                if (sum != 100) {
                    // Pour éviter les boucles infinies, on enlève temporairement les listeners
                    seekW1.setOnSeekBarChangeListener(null);
                    seekW2.setOnSeekBarChangeListener(null);
                    seekW3.setOnSeekBarChangeListener(null);
                    seekW4.setOnSeekBarChangeListener(null);
    
                    // Coefficient d’ajustement
                    float scale = 100f / sum;
    
                    // Nouvelle valeur = round(ancienne * scale)
                    int newP1 = Math.round(p1 * scale);
                    int newP2 = Math.round(p2 * scale);
                    int newP3 = Math.round(p3 * scale);
                    int newP4 = Math.round(p4 * scale);
    
                    // Vérif post-scaling (si la somme retombe à 99 ou 101 à cause d’arrondis,
                    // on peut ajuster 1 point sur la SeekBar la plus grande, par ex.)
                    int newSum = newP1 + newP2 + newP3 + newP4;
                    int diff = 100 - newSum; // ex: 1 ou -1, etc.
    
                    // On corrige diff sur la plus grande barre si besoin
                    if (diff != 0) {
                        // Chercher la plus grande barre pour y ajouter ou soustraire diff
                        int[] arr = {newP1, newP2, newP3, newP4};
                        int maxIndex = 0;
                        for (int i = 1; i < arr.length; i++) {
                            if (arr[i] > arr[maxIndex]) {
                                maxIndex = i;
                            }
                        }
                        arr[maxIndex] += diff;
    
                        newP1 = arr[0];
                        newP2 = arr[1];
                        newP3 = arr[2];
                        newP4 = arr[3];
                    }
    
                    // Assignation finale
                    seekW1.setProgress(newP1);
                    seekW2.setProgress(newP2);
                    seekW3.setProgress(newP3);
                    seekW4.setProgress(newP4);
    
                    // Rétablir les listeners
                    seekW1.setOnSeekBarChangeListener(this);
                    seekW2.setOnSeekBarChangeListener(this);
                    seekW3.setOnSeekBarChangeListener(this);
                    seekW4.setOnSeekBarChangeListener(this);
                }
            }
    
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { /* pas utilisé */ }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar)  { /* pas utilisé */ }
        };
    
        // Attacher ce listener à chaque SeekBar
        seekW1.setOnSeekBarChangeListener(seekBarChangeListener);
        seekW2.setOnSeekBarChangeListener(seekBarChangeListener);
        seekW3.setOnSeekBarChangeListener(seekBarChangeListener);
        seekW4.setOnSeekBarChangeListener(seekBarChangeListener);
    
        // 5) Créer le AlertDialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choisissez vos préférences");
        builder.setView(dialogView);
    
        // Bouton OK => on applique les changements
        builder.setPositiveButton("Valider", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // 1) Mettre à jour parkRelaisOnly
                parkRelaisOnly = checkBoxParkRelais.isChecked();
    
                // 2) Récupérer la valeur finale de chaque SeekBar
                float valW1 = seekW1.getProgress(); // 0..100
                float valW2 = seekW2.getProgress(); // 0..100
                float valW3 = seekW3.getProgress(); // 0..100
                float valW4 = seekW4.getProgress(); // 0..100
    
                // 3) Convertir en fraction 0..1 et normaliser (leur somme = 100 normalement)
                float sum = valW1 + valW2 + valW3 + valW4;
                if (sum == 0) {
                    w1 = w2 = w3 = w4 = 0.25f; // fallback
                } else {
                    w1 = valW1 / sum; 
                    w2 = valW2 / sum;
                    w3 = valW3 / sum;
                    w4 = valW4 / sum;
                }
    
                // 4) Afficher un message de confirmation
                String msg = "ParkRelaisOnly = " + parkRelaisOnly + "\n"
                           + "Distance^-1 : " + w1 + "\n"
                           + "Places libres: " + w2 + "\n"
                           + "Tarif^-1    : " + w3 + "\n"
                           + "Accès transp: " + w4;
    
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    
        // Bouton Annuler
        builder.setNegativeButton("Annuler", null);
    
        // 6) Afficher le dialog
        builder.create().show();
    }
    

    /**
     * Exemple de méthode pour calculer le score final d'un parking
     * en fonction de la distance, du tarif, etc.
     *
     * @param distance  Distance du parking (en km, par ex.)
     * @param freePlaces  Nombre de places libres
     * @param pricePerHour Tarif horaire
     * @param transportScore Score d'accès aux transports (0..1) ou un indicateur...
     * @return Le score calculé selon w1..w4
     */
    private float computeScore(float distance, int freePlaces, float pricePerHour, float transportScore) {
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

    /**
     * Redessine la ZFE si on a déjà chargé zfeData
     */
    private void drawZfe() {
        
        ZfeData zfeData = loadZfeDataFromJson("zone_faible_emission_bordeaux.json");
        if (zfeData == null || zfeData.coordinates == null) return;
        if (zfeData.coordinates.isEmpty()) return;

        PolygonOptions polygonOptions = new PolygonOptions();
        for (ZfeCoordinate coord : zfeData.coordinates) {
            polygonOptions.add(new LatLng(coord.lat, coord.lng));
        }

        polygonOptions.strokeColor(0xFFFF0000)  // Rouge opaque
                     .strokeWidth(5f)
                     .fillColor(0x33FF0000);    // Rouge semi-transparent

        mMap.addPolygon(polygonOptions);
    }
    

        /**
     * Méthode pour afficher les parkings sur la carte
     * @param seulementRelais true => on n'affiche que ceux dont le nom commence par "Parc-Relais"
     */
    private void afficherParkings(boolean seulementRelais) {
        // Nettoyage de la carte pour éviter d'empiler les markers (optionnel)
        mMap.clear();

        // Redessiner la ZFE après le nettoyage
        drawZfe();

        // Charger les données des parkings
        List<Parking> parkings = loadParkingsFromJson("st_park_p.json");
        if (parkings == null || parkings.isEmpty()) {
            Log.e("MainActivity", "Aucun parking trouvé ou erreur de parsing.");
            return;
        }

        // Préparer les icônes personnalisées pour les markers
        BitmapDescriptor icParkingMarker = getBitmapDescriptorFromDrawable(R.drawable.ic_parking);
        BitmapDescriptor icParkRelaisMarker = getBitmapDescriptorFromDrawable(R.drawable.ic_park_relais);

        int countAffiches = 0;
        for (Parking p : parkings) {
            if (p.geo_point_2d == null || p.nom == null) {
                // Parking invalide
                continue;
            }

            // Filtre si l'utilisateur veut seulement les "Parc-Relais"
            if (seulementRelais && !p.nom.startsWith("Parc-Relais")) {
                continue; // Ignorer ce parking
            }

            double lat = p.geo_point_2d.lat;
            double lon = p.geo_point_2d.lon;

            // Déterminer l'icône à utiliser
            BitmapDescriptor markerIcon = p.nom.startsWith("Parc-Relais") ? icParkRelaisMarker : icParkingMarker;

            // Ajouter un marker avec l'icône appropriée
            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(lat, lon))
                    .title(p.nom)
                    .snippet(p.adresse) // adresse en sous-titre, si dispo
                    .icon(markerIcon) // Utiliser l'icône personnalisée
            );

            marker.setTag(p);

            countAffiches++;
        }

        Log.i("MainActivity",
            "Parkings affichés : " + countAffiches
            + (seulementRelais ? " (mode Relais)" : " (mode Tous)"));
    }

    

    /**
     * Convertit une ressource drawable en BitmapDescriptor pour l'utiliser dans les markers
     * @param resId ID de la ressource drawable
     * @return BitmapDescriptor à utiliser dans les MarkerOptions
     */
    private BitmapDescriptor getBitmapDescriptorFromDrawable(int resId) {
        Drawable drawable = ContextCompat.getDrawable(this, resId);
        if (drawable == null) {
            Log.e("MainActivity", "Drawable introuvable : " + resId);
            return null;
        }

        // Ajuster la taille du drawable
        int width = 80; // Largeur en pixels (adaptez selon vos besoins)
        int height = 80; // Hauteur en pixels
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }


    private void showItineraryDialog(Parking parking) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(parking.nom);
        builder.setMessage("Voulez-vous calculer l'itinéraire vers ce parking ?");
        
        builder.setPositiveButton("Oui", (dialog, which) -> {
            // Calcul d’itinéraire, par ex. fetchDirections(...)
            if (currentUserLocation != null) {
                fetchDirections(currentUserLocation, 
                    new LatLng(parking.geo_point_2d.lat, parking.geo_point_2d.lon), 
                    "driving");
            } else {
                Toast.makeText(this, "Position courante indisponible", Toast.LENGTH_SHORT).show();
            }
        });
    
        builder.setNegativeButton("Annuler", null);
        builder.show();
    }
    



    // ------------------------------------
    // 2. Recherche du parking optimal
    // ------------------------------------
    private Parking findBestParking(LatLng userLoc) {
        // 1) Charger la liste de parkings
        List<Parking> allParkings = loadParkingsFromJson("st_park_p.json");
        if (allParkings == null || allParkings.isEmpty()) {
            return null;
        }

        Parking best = null;
        float bestScore = -9999f;

        for (Parking p : allParkings) {
            // Filtre Park-Relais si nécessaire
            if (parkRelaisOnly) {
                if (p.nom == null || !p.nom.startsWith("Parc-Relais")) {
                    // On ignore ce parking
                    continue;
                }
            }
            // Calculer la distance entre userLoc et parking
            float distance = computeDistance(userLoc, 
                                             new LatLng(p.geo_point_2d.lat, p.geo_point_2d.lon));
            // On suppose qu'on a p.placesLibres, p.tarifHoraire, p.transportScore, etc.
            // S'ils n'existent pas, adaptez le code.
            int places = p.libres;
            float price = p.tarifHoraire;
            float transport = p.transportScore; // ou un calcul interne

            float score = computeScore(distance, places, price, transport);
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    private float computeDistance(LatLng latLng1, LatLng latLng2) {
        float[] results = new float[1];
        android.location.Location.distanceBetween(
                latLng1.latitude, latLng1.longitude,
                latLng2.latitude, latLng2.longitude,
                results);
        // results[0] est la distance en mètres
        return results[0] / 1000f; // en km
    }


    

    /**
     * Méthode callback appelée lorsque la carte est prête à être utilisée.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Centrage de la carte sur Bordeaux
        LatLng bordeaux = new LatLng(44.8378, -0.5792);
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(bordeaux)
                .zoom(10f)
                .build();
        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

        enableUserLocation();

        // Charger l'objet ZFE depuis le fichier JSON et dessiner le polygone
        ZfeData zfeData = loadZfeDataFromJson("zone_faible_emission_bordeaux.json");
        
        drawZfe();

        mMap.setOnMapClickListener(latLng -> {
            if (isChoosingOrigin) {
                originLatLng = latLng;
                isChoosingOrigin = false;
                Toast.makeText(this, "Origine définie : " + latLng.toString(), Toast.LENGTH_SHORT).show();
            } else if (isChoosingDest) {
                destLatLng = latLng;
                isChoosingDest = false;
                Toast.makeText(this, "Destination définie : " + latLng.toString(), Toast.LENGTH_SHORT).show();
            }
        });


        mMap.setOnInfoWindowClickListener(marker -> {
            // Récupérer l'objet Parking
            Parking clickedParking = (Parking) marker.getTag();
            if (clickedParking != null) {
                // Soit on lance directement l'itinéraire,
                // Soit on ouvre un Dialog proposant "Calculer Itinéraire" etc.
        
                showItineraryDialog(clickedParking);
            }
        });
        

        
    }

    /**
     * Charge le fichier JSON et le convertit en un objet ZfeData.
     */
    private ZfeData loadZfeDataFromJson(String fileName) {
        try {
            // Ouvrir le fichier dans /assets/
            InputStream is = getAssets().open(fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));

            // Parser le JSON avec Gson
            Gson gson = new Gson();
            ZfeData zfeData = gson.fromJson(reader, ZfeData.class);

            reader.close();
            is.close();
            return zfeData;

        } catch (Exception e) {
            Log.e("MainActivity", "Erreur lors de la lecture du JSON : " + e.getMessage(), e);
        }
        return null;
    }


    /**
     * Charge la liste de parkings depuis le fichier st_park_p.json.
     * On suppose que st_park_p.json est un tableau JSON de Parkings.
     */

    private List<Parking> loadParkingsFromJson(String fileName) {
        try {
            InputStream is = getAssets().open(fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));

            // On parse un tableau JSON -> Parking[]
            Gson gson = new Gson();
            Parking[] parkingsArray = gson.fromJson(reader, Parking[].class);

            reader.close();
            is.close();

            // Convertir le tableau en List
            return java.util.Arrays.asList(parkingsArray);

        } catch (Exception e) {
            Log.e("MainActivity", "Erreur lors de la lecture du JSON Parkings : " + e.getMessage(), e);
        }
        return null;
    }


    /**
     * Géré la réponse de la demande de permission
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, 
                                          @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && 
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission accordée
                enableUserLocation();
            } else {
                // Permission refusée
                Toast.makeText(this, "Permission localisation refusée", 
                               Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Méthode appelée quand on clique sur "Ma Localisation"
     * (bouton en bas à droite)
     */
    private void goToMyLocation() {
        if (mMap == null) return;

        // Vérifier permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            // (1) Méthode DECONSEILLÉE (mais simple) : getMyLocation()
            /*Location location = mMap.getMyLocation();
            /*if (location != null) {
                double lat = location.getLatitude();
                double lng = location.getLongitude();
                LatLng myLatLng = new LatLng(lat, lng);
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLatLng, 15f));
            } else {
                Toast.makeText(this, 
                    "Localisation non disponible pour l'instant...", 
                    Toast.LENGTH_SHORT).show();
            }*/

            
             // (2) METHODE RECOMMANDEE: FusedLocationProviderClient
             FusedLocationProviderClient fusedLocationClient =
                    LocationServices.getFusedLocationProviderClient(this);
                fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            LatLng myLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                            currentUserLocation = myLatLng;
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLatLng, 15f));
                        } else {
                            Toast.makeText(this, 
                                "Localisation non disponible...", 
                                Toast.LENGTH_SHORT).show();
                        }
                    });
             
        } else {
            Toast.makeText(this, 
                "Permission non accordée, impossible de vous localiser.", 
                Toast.LENGTH_SHORT).show();
        }
    }


    public void setMockLocation(LocationManager locationManager) {
        try {
            String providerName = LocationManager.GPS_PROVIDER;
    
            // Activer le provider fictif
            locationManager.addTestProvider(
                providerName,
                false, false, false, false, true, true, true, 0, 5
            );
            locationManager.setTestProviderEnabled(providerName, true);
    
            // Définir la localisation fictive
            Location mockLocation = new Location(providerName);
            mockLocation.setLatitude(44.8378); // Latitude de Bordeaux
            mockLocation.setLongitude(-0.5792); // Longitude de Bordeaux
            mockLocation.setAltitude(10); // Altitude fictive
            mockLocation.setAccuracy(5); // Précision en mètres
            mockLocation.setTime(System.currentTimeMillis());
            mockLocation.setElapsedRealtimeNanos(System.nanoTime());
    
            // Injecter la localisation
            locationManager.setTestProviderLocation(providerName, mockLocation);
    
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }


    private void updateZfeAlert(LatLng userLocation) {
        
        
        if (userLocation == null) {
            Log.e("ZFE", "Impossible de mettre à jour l'alerte : userLocation est null");
            return;
        }

        if (zfePolygon == null || zfePolygon.isEmpty()) {
            // Sécurité : ZFE pas chargée
            Log.i("ZFE", "ZFE polygon is not loaded4" + zfePolygon);
            showZfeAlert("Alerte ZFE : vous êtes à < 200 m !!");
            return;
        }
    
        boolean inside = PolyUtil.containsLocation(currentUserLocation, zfePolygon, false);
        if (inside) {
            showZfeAlert("Alerte ZFE : vous êtes dans la zone !");
            Log.i("ZFE", "ZFE polygon is not loaded3");
        } else {
            // Calcul min distance
            double minDistance = calcMinDistance(currentUserLocation, zfePolygon);
            if (minDistance < 200.0) {
                showZfeAlert("Alerte ZFE : vous êtes à < 200 m !");
                Log.i("ZFE", "ZFE polygon is not loaded");
            } else {
                showZfeOk("Situation ZFE : Aucun risque11");
                Log.i("ZFE", "ZFE polygon is not loaded2");
            }
        }
    }

    
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

    private void showZfeOptionsDialog() {
        // 1) Inflater la vue personnalisée
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_zfe_alert, null);
    
        // 2) Récupérer les éléments du layout
        ImageView ivIcon          = dialogView.findViewById(R.id.ivZfeIcon);
        TextView tvMessage        = dialogView.findViewById(R.id.tvZfeMessage);
        Button btnChangerItin     = dialogView.findViewById(R.id.btnChangeItineraire);
        Button btnFindParking     = dialogView.findViewById(R.id.btnFindParking);
        Button btnCancel          = dialogView.findViewById(R.id.btnCancel);
    
        // (Optionnel) Modifier le texte ou l’icône dynamiquement
        // tvMessage.setText("Le trajet passe dans la zone ZFE. Que souhaitez-vous faire ?");
        // ivIcon.setImageResource(R.drawable.ic_custom_icon);
    
        // 3) Construire le AlertDialog avec la vue personnalisée
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ZfeAlertDialogTheme);
        builder.setView(dialogView);


    
        // 4) Créer et afficher le dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    
        // 5) Gérer les événements (clicks) sur les boutons
        btnChangerItin.setOnClickListener(v -> {
            //  -> Changer l’itinéraire
            dialog.dismiss(); // Ferme le dialog
            avoidZfeAndRecalculateRoute();
        });
    
        btnFindParking.setOnClickListener(v -> {
            //  -> Trouver un parking
            dialog.dismiss(); // Ferme le dialog
            LatLng positionCible = (destLatLng != null) ? destLatLng : currentUserLocation;
            Parking bestParking = findBestParking(positionCible);
            if (bestParking != null) {
                suggestParking(bestParking);
            } else {
                Toast.makeText(MainActivity.this,
                    "Aucun parking optimal trouvé à proximité.",
                    Toast.LENGTH_SHORT).show();
            }
        });
    
        btnCancel.setOnClickListener(v -> {
            //  -> Annuler, fermer le dialog
            dialog.dismiss();
        });
    }
    
    

    /**
 * Construit l'URL pour l'API Directions en incluant un waypoint afin de contourner la ZFE.
 * @param origin      Coordonnées de l’origine
 * @param destination Coordonnées de la destination
 * @param waypoint    Coordonnées du waypoint (point par lequel on veut forcer le passage)
 * @param mode        Mode de déplacement (ex: "driving", "walking", etc.)
 * @return            Une chaîne de caractère contenant l’URL pour la requête Directions
 */
    private String buildDirectionsUrlWithWaypoint(LatLng origin, LatLng destination, 
    LatLng waypoint, String mode) {
        String strOrigin = "origin=" + origin.latitude + "," + origin.longitude;
        String strDest   = "destination=" + destination.latitude + "," + destination.longitude;

        // Important: on ajoute ici &waypoints= pour forcer le passage par un point
        String strWaypoint = "waypoints=" + waypoint.latitude + "," + waypoint.longitude;
        String travelMode = "mode=" + mode;

        // Remplacez par votre propre clé Google Maps Directions
        String apiKey = "key=VOTRE_CLE_DIRECTIONS_ICI"; 

        // Construit l'URL avec les différents paramètres
        return "https://maps.googleapis.com/maps/api/directions/json?"
        + strOrigin + "&"
        + strDest + "&"
        + strWaypoint + "&"
        + travelMode + "&"
        + apiKey;
    }



    private void avoidZfeAndRecalculateRoute() {
        if (originLatLng == null || destLatLng == null) {
            Toast.makeText(MainActivity.this,
                    "Impossible de recalculer un itinéraire : origine ou destination manquante.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
    
        // Exemple de waypoint pour contourner la zone (vous adapterez les coordonnées)
        LatLng altWaypoint = new LatLng(44.90, -0.60);
    
        // Construction de l'URL avec le waypoint forcé
        String url = buildDirectionsUrlWithWaypoint(originLatLng, destLatLng, altWaypoint, "driving");
    
        // Appel à fetchDirectionsWithCallback en lui passant un Callback
        fetchDirectionsWithCallback(url, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                // Erreur réseau ou autre
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, 
                            "Échec lors du recalcul d'itinéraire", 
                            Toast.LENGTH_SHORT).show();
                });
            }
    
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    // Gérer le cas où l'API ne répond pas un code 200
                    Log.e("Directions", "Response not successful: " + response.code());
                    return;
                }
    
                // Récupère la réponse (JSON) sous forme de String
                String body = response.body().string();
    
                // Traitement identique ou similaire à votre parseDirectionsResponse(...)
                parseAlternativeDirectionsResponse(body);
            }
        });
    }


    private List<LatLng> calculateWaypointsToAvoidZfe(LatLng origin, LatLng destination, List<LatLng> zfePolygon) {
        List<LatLng> waypoints = new ArrayList<>();
    
        // Example: Add nearest vertices of the polygon as waypoints
        for (LatLng point : zfePolygon) {
            if (true ){//distanceToLineSegment(origin, destination, point) > 100) { // Threshold distance
                waypoints.add(point);
            }
        }
    
        return waypoints;
    }


    private void fetchRouteAvoidingZfe(LatLng origin, LatLng destination, List<LatLng> zfePolygon) {
        // Generate waypoints to bypass the ZFE
        List<LatLng> waypoints = calculateWaypointsToAvoidZfe(origin, destination, zfePolygon);
    
        if (waypoints.isEmpty()) {
            Toast.makeText(this, "Impossible to find waypoints to avoid the ZFE.", Toast.LENGTH_SHORT).show();
            return;
        }
    
        // Build waypoints string for the API
        StringBuilder waypointsString = new StringBuilder();
        for (LatLng point : waypoints) {
            if (waypointsString.length() > 0) {
                waypointsString.append("|");
            }
            waypointsString.append(point.latitude).append(",").append(point.longitude);
        }
    
        // Construct the API URL
        String apiKey = "YOUR_API_KEY";
        String url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=" + origin.latitude + "," + origin.longitude +
                "&destination=" + destination.latitude + "," + destination.longitude +
                "&waypoints=via:" + waypointsString +
                "&mode=driving" +
                "&key=" + apiKey;
    
        // Fetch the route using OkHttp or another HTTP client
        fetchDirectionsFromApi(url);
    }
    
    private void fetchDirectionsFromApi(String url) {
        OkHttpClient client = new OkHttpClient();
    
        Request request = new Request.Builder()
                .url(url)
                .build();
    
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Failed to fetch directions", Toast.LENGTH_SHORT).show();
                });
            }
    
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e("DirectionsAPI", "Response not successful: " + response.code());
                    return;
                }
    
                String responseBody = response.body().string();
                runOnUiThread(() -> {
                    // Parse and display the route
                    parseAndDisplayRoute(responseBody);
                });
            }
        });
    }
    
    private void parseAndDisplayRoute(String jsonResponse) {
        try {
            // Parse the JSON response
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONArray routesArray = jsonObject.getJSONArray("routes");
            
            // Check if there are any routes
            if (routesArray.length() == 0) {
                Log.i("Directions", "No routes found in response");
                Toast.makeText(this, "Aucun itinéraire trouvé.", Toast.LENGTH_SHORT).show();
                return;
            }
    
            // Take the first route
            JSONObject route = routesArray.getJSONObject(0);
            JSONObject overviewPolyline = route.getJSONObject("overview_polyline");
            String encodedPoints = overviewPolyline.getString("points");
    
            // Decode the polyline into a list of LatLng
            List<LatLng> decodedPath = PolyUtil.decode(encodedPoints);
    
            // Display the route on the map
            runOnUiThread(() -> {
                // Clear any existing polylines (optional)
                mMap.clear();
                drawZfe(); // Redraw the ZFE polygon
    
                // Add the route polyline
                PolylineOptions polylineOptions = new PolylineOptions()
                        .addAll(decodedPath)
                        .color(Color.BLUE)  // Set the polyline color
                        .width(10f);        // Set the polyline width
                mMap.addPolyline(polylineOptions);
    
                // Adjust the camera to fit the route
                LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
                for (LatLng point : decodedPath) {
                    boundsBuilder.include(point);
                }
                LatLngBounds bounds = boundsBuilder.build();
                int padding = 100; // Add padding (pixels) around the route
                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
    
                Toast.makeText(this, "Itinéraire affiché.", Toast.LENGTH_SHORT).show();
            });
    
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("Directions", "Erreur lors de l'analyse de la réponse : " + e.getMessage());
            runOnUiThread(() -> {
                Toast.makeText(this, "Erreur lors de l'affichage de l'itinéraire.", Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    
    
    /**
     * Méthode "générique" de récupération d'itinéraire, 
     * similaire à fetchDirections(...) mais prenant un Callback en paramètre
     */
    private void fetchDirectionsWithCallback(String url, Callback callback) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();
    
        client.newCall(request).enqueue(callback);
    }
    
    /**
     * Ex. de parsing alternatif (vous pouvez aussi réutiliser parseDirectionsResponse(body))
     */
    private void parseAlternativeDirectionsResponse(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONArray routesArray = jsonObject.getJSONArray("routes");
            if (routesArray.length() == 0) {
                Log.i("Directions", "No alternative routes found");
                return;
            }
            JSONObject route = routesArray.getJSONObject(0);
            JSONObject overview = route.getJSONObject("overview_polyline");
            String encodedPoints = overview.getString("points");
    
            List<LatLng> decodedPath = PolyUtil.decode(encodedPoints);
    
            // Affichage sur la map
            runOnUiThread(() -> {
                // Supprimez éventuellement l'ancien tracé si besoin
                mMap.clear();
                drawZfe();
    
                PolylineOptions polyOptions = new PolylineOptions()
                        .addAll(decodedPath)
                        .color(Color.MAGENTA)  // Couleur différente pour distinguer l'alternative
                        .width(12f);
                mMap.addPolyline(polyOptions);
    
                // Ajustement de la caméra
                LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
                for (LatLng point : decodedPath) {
                    boundsBuilder.include(point);
                }
                LatLngBounds bounds = boundsBuilder.build();
                int padding = 100;
                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
    
                Toast.makeText(MainActivity.this, 
                    "Itinéraire alternatif tracé (contournement ZFE)", 
                    Toast.LENGTH_SHORT).show();
            });
    
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    

    
    private void showZfeAlert(String message) {
        runOnUiThread(() -> {
            TextView zfeAlertText = findViewById(R.id.zfeAlertText);
            zfeAlertText.setText(message);
            zfeAlertText.setTextColor(Color.RED); // Couleur d'alerte
        });
    }
    
    private void showZfeOk(String message) {
        runOnUiThread(() -> {
            TextView zfeAlertText = findViewById(R.id.zfeAlertText);
            zfeAlertText.setText(message);
            zfeAlertText.setTextColor(Color.GREEN); // Couleur OK
        });
    }
    



    // -----------------------------------
    //  REQUÊTE DIRECTIONS
    // -----------------------------------
    private void fetchDirections(LatLng origin, LatLng destination, String mode) {
        String url = buildDirectionsUrl(origin, destination, mode);

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
            .url(url)
            .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response)
                    throws IOException {
                if (!response.isSuccessful()) {
                    Log.e("Directions", "Response not successful");
                    return;
                }
                String body = response.body().string();
                parseDirectionsResponse(body);
            }
        });
    }

    private String buildDirectionsUrl(LatLng origin, LatLng destination, String mode) {
        String strOrigin = "origin=" + origin.latitude + "," + origin.longitude;
        String strDest   = "destination=" + destination.latitude + "," + destination.longitude;
        String travelMode = "mode=" + mode;
        String apiKey = "key=AIzaSyAr43ZCeCPVpWX-djpFLGAcpz-cH-ppwsk"; // Remplacez par votre clé

        
        return "https://maps.googleapis.com/maps/api/directions/json?"
                + strOrigin + "&"
                + strDest + "&"
                + travelMode + "&"
                + apiKey;
    }

    private void parseDirectionsResponse(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            Log.i("test", jsonResponse);
            JSONArray routesArray = jsonObject.getJSONArray("routes");
            if (routesArray.length() == 0) {
                Log.i("Directions", "No routes found");
                return;
            }
            // On prend la première route
            JSONObject route = routesArray.getJSONObject(0);
            JSONObject overview = route.getJSONObject("overview_polyline");
            String encodedPoints = overview.getString("points");

            List<LatLng> decodedPath = PolyUtil.decode(encodedPoints);

            runOnUiThread(() -> {
                showRouteOnMap(decodedPath);
                handleDefinedRoute(decodedPath, destLatLng);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showRouteOnMap(List<LatLng> route) {
        if (mMap == null){
            Log.i("Directions1", "No routes found");
            return;
        }
        PolylineOptions polyOptions = new PolylineOptions()
                .addAll(route)
                .color(Color.BLUE)
                .width(12f);
        mMap.addPolyline(polyOptions);
        Log.i("Directions2", "No routes found");

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        for (LatLng point : route) {
            boundsBuilder.include(point);
        }
        LatLngBounds bounds = boundsBuilder.build();
        int padding = 100; // marge en pixels
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
    }
    

    // Demande la position GPS courante pour l'origine
    private void setOriginFromGPS() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    originLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    Toast.makeText(this, "Origine (GPS) définie", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Position indisponible...", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }


    private void startLocationUpdates() {
        // Vérifiez si la permission est accordée
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
    
        // Configurez une demande de localisation
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(10000); // Toutes les 10 secondes
        locationRequest.setFastestInterval(5000); // Temps minimal entre deux mises à jour
    
        // Récupérez les mises à jour de localisation
        fusedLocationClient.requestLocationUpdates(locationRequest, new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
    
                // Obtenez la dernière localisation
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    currentUserLocation = new LatLng(location.getLatitude(), location.getLongitude());
                    Log.i("MainActivity", "Localisation actuelle : " + currentUserLocation);
                }
            }
        }, Looper.getMainLooper());
    }

    private void handleDefinedRoute(List<LatLng> route, LatLng destination) {
        boolean passesThroughZfe = false;
        
    
        // Vérifier si l'itinéraire passe par une ZFE
        for (LatLng point : route) {
            if (PolyUtil.containsLocation(point, zfePolygon, false)) {
                passesThroughZfe = true;
                Log.i("tesrt", "hani"+ passesThroughZfe + zfePolygon);
                break;
            }
        }
        Log.i("tesrt", "hani5"+ passesThroughZfe + zfePolygon);

        // Vérifier si la destination est dans une ZFE
        boolean destinationInZfe = PolyUtil.containsLocation(destination, zfePolygon, false);
        boolean finalPassesThroughZfe = passesThroughZfe;
        runOnUiThread(() -> {
            if (destinationInZfe) {
                showZfeAlert("Attention : la destination est dans la ZFE !");
                Log.i("tesrt", "hani1");
                // On propose de faire quelque chose : parking ou autre
                showZfeOptionsDialog();
            } else if (finalPassesThroughZfe) {
                showZfeAlert("Attention : l'itinéraire passe par la ZFE !");
                Log.i("tesrt", "hani2");
                // Afficher la boîte de dialogue
                showZfeOptionsDialog();
            } else {
                showZfeOk("Itinéraire conforme : pas de passage dans une ZFE.");
                Log.i("tesrt", "hani3"+ finalPassesThroughZfe);
            }
        });
    }
    
    // Suggère un parking spécifique sur la carte
    private void suggestParking(Parking parking) {
        LatLng parkingLocation = new LatLng(parking.geo_point_2d.lat, parking.geo_point_2d.lon);
        mMap.addMarker(new MarkerOptions()
                .position(parkingLocation)
                .title("Parking suggéré : " + parking.nom)
                .snippet(parking.adresse));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(parkingLocation, 15f));
        Toast.makeText(this, "Parking suggéré : " + parking.nom, Toast.LENGTH_LONG).show();
    }


    /**
     * Lit la vitesse actuelle depuis le fichier vitesse.txt.
     * @return La vitesse en km/h ou -1 si une erreur survient.
     */
    private float readSpeedFromFile() {
        float speed = -1f; // Valeur par défaut invalide
        try {
            // Ouvrir le fichier dans le stockage interne
            FileInputStream fis = openFileInput(SPEED_FILE_NAME);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            String line = reader.readLine();
            reader.close();
            fis.close();

            if (line != null && !line.isEmpty()) {
                speed = Float.parseFloat(line.trim());
            }
        } catch (IOException e) {
            Log.e("MainActivity", "Erreur lors de la lecture de " + SPEED_FILE_NAME, e);
        } catch (NumberFormatException e) {
            Log.e("MainActivity", "Format de vitesse invalide dans " + SPEED_FILE_NAME, e);
        }
        return speed;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Retirer les callbacks pour prévenir les fuites de mémoire
        speedUpdateHandler.removeCallbacks(speedUpdateRunnable);
    }

    private void createSpeedFileIfNotExists() {
        File file = new File(getFilesDir(), SPEED_FILE_NAME);
        if (!file.exists()) {
            try {
                FileOutputStream fos = openFileOutput(SPEED_FILE_NAME, MODE_PRIVATE);
                fos.write("50".getBytes()); // Vitesse initiale de 50 km/h
                fos.close();
            } catch (IOException e) {
                Log.e("MainActivity", "Erreur lors de la création de " + SPEED_FILE_NAME, e);
            }
        }
    }
    
    
    
    
}
