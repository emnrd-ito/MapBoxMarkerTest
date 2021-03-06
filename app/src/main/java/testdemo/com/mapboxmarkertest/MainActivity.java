package testdemo.com.mapboxmarkertest;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.mapbox.mapboxsdk.MapboxAccountManager;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.MarkerViewOptions;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.services.Constants;
import com.mapbox.services.commons.ServicesException;
import com.mapbox.services.commons.geojson.LineString;
import com.mapbox.services.commons.models.Position;
import com.mapbox.services.directions.v5.DirectionsCriteria;
import com.mapbox.services.directions.v5.MapboxDirections;
import com.mapbox.services.directions.v5.models.DirectionsResponse;
import com.mapbox.services.directions.v5.models.DirectionsRoute;

import java.text.DecimalFormat;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "MainActivity";

    private MapView mapView;
    private MapboxMap map;
    private DirectionsRoute currentRoute;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Mapbox access token is configured here. This needs to be called either in your application
        // object or in the same activity which contains the mapview.
        MapboxAccountManager.start(this, getString(R.string.access_token));

        setContentView(R.layout.activity_main);

        double originLatitude = Double.parseDouble(getString(R.string.nyc_union_square_latitude));
        double originLongitude = Double.parseDouble(getString(R.string.nyc_union_square_longitude));

        double destinationLatitude = Double.parseDouble(getString(R.string.boston_harbor_latitude));
        double destinationLongitude = Double.parseDouble(getString(R.string.boston_harbor_longitude));

        final Position origin = Position.fromCoordinates(originLongitude, originLatitude);

        final Position destination = Position.fromCoordinates(destinationLongitude, destinationLatitude);

        this.setTitle("Origin: (" + origin.getLatitude() + ", " + origin.getLongitude() + ")  >>  Destination: (" + destination.getLatitude() + ", " + destination.getLongitude() + ")" );

        // Create Icon objects for the marker to use
        IconFactory iconFactory = IconFactory.getInstance(this);
        Drawable iconDrawable = ContextCompat.getDrawable(this, R.drawable.green_pin); // pin png is 125x125
        final Icon greenPinIcon = iconFactory.fromDrawable(iconDrawable);
        iconDrawable = ContextCompat.getDrawable(this, R.drawable.red_pin); // pin png is 125x125
        final Icon redPinIcon = iconFactory.fromDrawable(iconDrawable);

        // Setup the MapView
        mapView = (MapView) findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                map = mapboxMap;

                // Add origin and destination to the map
                LatLng originLatLng = (new LatLng(origin.getLatitude(), origin.getLongitude()));
                mapboxMap.addMarker(new MarkerViewOptions()
                        .position(originLatLng)
                        //.anchor((float)0.5, (float)1.0) // bottom, middle I think
                        .title("Origin")
                        .snippet("current location: (" + origin.getLatitude() + ", " + origin.getLongitude() + ")")
                        .icon(greenPinIcon)); // custom icon's position is a little off

                LatLng destinationLatLng = (new LatLng(destination.getLatitude(), destination.getLongitude()));
                mapboxMap.addMarker(new MarkerViewOptions()
                        .position(destinationLatLng)
                        //.anchor((float)0.5, (float)1.0) // bottom, middle I think
                        .title("Destination")
                        .snippet("destination: (" + destination.getLatitude() + ", " + destination.getLongitude() + ")")
                        .icon(redPinIcon)); // custom icon's position is a little off

                LatLngBounds latLngBounds = new LatLngBounds.Builder()
                        .include(originLatLng) // Northeast
                        .include(destinationLatLng) // Southwest
                        .build();

                mapboxMap.setPadding(50,50,50,50);

                mapboxMap.easeCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds, 50), 5000);

                // Get route from API
                try {
                    getRoute(origin, destination);
                }
                catch (ServicesException servicesException) {
                    Log.e(TAG, servicesException.toString());
                    servicesException.printStackTrace();
                }
            }
        });

    }

    private void getRoute(Position origin, Position destination) throws ServicesException {

        MapboxDirections client = new MapboxDirections.Builder()
                .setOrigin(origin)
                .setDestination(destination)
                .setProfile(DirectionsCriteria.PROFILE_CYCLING)
                .setAccessToken(MapboxAccountManager.getInstance().getAccessToken())
                .build();

        client.enqueueCall(new Callback<DirectionsResponse>() {
            @Override
            public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                // You can get the generic HTTP info about the response
                Log.d(TAG, "Response code: " + response.code());
                if (response.body() == null) {
                    Log.e(TAG, "No routes found, make sure you set the right user and access token.");
                    return;
                } else if (response.body().getRoutes().size() < 1) {
                    Log.e(TAG, "No routes found");
                    return;
                }

                // Print some info about the route
                currentRoute = response.body().getRoutes().get(0);
                Log.d(TAG, "Distance: " + currentRoute.getDistance());
                Toast.makeText(
                        MainActivity.this,
                        "Route is " + currentRoute.getDistance() + " meters long.",
                        Toast.LENGTH_SHORT).show();

                // Draw the route on the map
                drawRoute(currentRoute);
            }

            @Override
            public void onFailure(Call<DirectionsResponse> call, Throwable throwable) {
                Log.e(TAG, "Error: " + throwable.getMessage());
                Toast.makeText(MainActivity.this, "Error: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void drawRoute(DirectionsRoute route) {
        // Convert LineString coordinates into LatLng[]
        LineString lineString = LineString.fromPolyline(route.getGeometry(), Constants.OSRM_PRECISION_V5);
        List<Position> coordinates = lineString.getCoordinates();
        LatLng[] points = new LatLng[coordinates.size()];
        for (int i = 0; i < coordinates.size(); i++) {
            points[i] = new LatLng(
                    coordinates.get(i).getLatitude(),
                    coordinates.get(i).getLongitude());
        }

        // Draw Points on MapView
        map.addPolyline(new PolylineOptions()
                .add(points)
                .color(Color.parseColor("#009688"))
                .width(5));
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}
