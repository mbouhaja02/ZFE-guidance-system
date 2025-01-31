import json
import requests

# Définir la clé API Google Maps et l'URL de base
API_KEY = "AIzaSyDyqmhb_Lubhz3BZBTSeIEPVevbj-9fCg4"
GEOCODING_BASE_URL = "https://maps.googleapis.com/maps/api/geocode/json"

# Fonction pour obtenir les coordonnées géographiques et l'adresse formatée
def get_geocode_data(address):
    params = {
        "address": address,
        "key": API_KEY
    }
    try:
        response = requests.get(GEOCODING_BASE_URL, params=params)
        if response.status_code == 200:
            data = response.json()
            if data["results"]:
                location = data["results"][0]["geometry"]["location"]
                formatted_address = data["results"][0]["formatted_address"]
                return {
                    "latitude": location["lat"],
                    "longitude": location["lng"],
                    "formatted_address": formatted_address
                }
            else:
                print(f"No results found for address: {address}")
                return None
        else:
            print(f"Error fetching data for address: {address}, Status code: {response.status_code}")
            return None
    except Exception as e:
        print(f"Exception during geocoding for address: {address}, Error: {e}")
        return None

# Fonction pour calculer le point milieu entre deux coordonnées
def calculate_midpoint(coord1, coord2):
    lat = (coord1[0] + coord2[0]) / 2
    lng = (coord1[1] + coord2[1]) / 2
    return lat, lng

# Charger le fichier JSON d'entrée
input_file = "ci_acte_a.json"  # Nom du fichier d'entrée
output_file = "output_with_geocode.json"

try:
    with open(input_file, "r", encoding="utf-8") as f:
        data = json.load(f)
except FileNotFoundError:
    print(f"Input file {input_file} not found.")
    exit()

# Traiter chaque élément dans le fichier JSON
for item in data:
    localisation_emprise = item.get("localisation_emprise")
    if not localisation_emprise:
        print(f"Error: Missing localisation_emprise in item: {item}")
        item["geo_data"] = None
        continue

    addresses = localisation_emprise.split("/")
    geocode_results = []

    for address in addresses:
        address = address.strip()
        geocode_data = get_geocode_data(address)
        if geocode_data:
            geocode_results.append(geocode_data)

    if len(geocode_results) == 1:
        # Une seule adresse, utiliser ses données de géocodage
        item["geo_data"] = geocode_results[0]
    elif len(geocode_results) >= 2:
        # Deux adresses ou plus, calculer le point milieu
        midpoint = calculate_midpoint(
            (geocode_results[0]["latitude"], geocode_results[0]["longitude"]),
            (geocode_results[1]["latitude"], geocode_results[1]["longitude"])
        )
        item["geo_data"] = {
            "latitude": midpoint[0],
            "longitude": midpoint[1],
            "formatted_address": f"Midpoint of {geocode_results[0]['formatted_address']} and {geocode_results[1]['formatted_address']}"
        }
    else:
        print(f"Error processing localisation_emprise: {localisation_emprise}")
        item["geo_data"] = None

# Sauvegarder le JSON mis à jour dans un nouveau fichier
try:
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=4, ensure_ascii=False)
    print(f"Updated JSON with geocode data saved to {output_file}")
except Exception as e:
    print(f"Error saving output file: {e}")
