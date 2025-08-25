# OpenStreetMap Migration Guide

This document explains how to migrate from Google Maps to OpenStreetMap in your SAGIPP application.

## Overview

The application now supports OpenStreetMap as an alternative to Google Maps, providing the same functionality but using free, open-source mapping data. This eliminates the need for Google Maps API keys and reduces costs.

## What's Changed

### 1. Dependencies
- **Removed**: Google Maps SDK (`com.google.android.gms:play-services-maps`)
- **Added**: OpenStreetMap SDK (`org.osmdroid:osmdroid-android:6.1.18`)

### 2. New Files Created
- `MyOpenStreetMap.java` - Main OpenStreetMap implementation
- `activity_my_openstreet_map.xml` - Layout for OpenStreetMap activity
- `OpenStreetMapExample.java` - Example activity to test different modes
- `activity_openstreet_map_example.xml` - Layout for example activity

### 3. Modified Files
- `build.gradle.kts` - Updated dependencies
- `AndroidManifest.xml` - Added new activities and permissions

## Features Maintained

The OpenStreetMap implementation maintains all the original Google Maps functionality:

### ✅ Core Features
- **Location Display**: Show emergency locations on the map
- **Real-time Location Updates**: Track user location using GPS
- **Route Calculation**: Calculate routes between points using OSRM API
- **Distance & Time Estimation**: Show estimated travel distance and time
- **Marker Management**: Add, remove, and update map markers
- **Camera Controls**: Zoom, pan, and center map view

### ✅ Emergency Features
- **Rescuer Mode**: Rescuers can view emergency locations and navigate to them
- **Senior Tracking Mode**: Seniors can track approaching rescuers
- **Emergency Mode**: Display emergency locations for seniors
- **Real-time Rescuer Tracking**: Track multiple rescuers on the map
- **Emergency Notifications**: Send notifications when rescuers respond

### ✅ Navigation Features
- **Route Display**: Show turn-by-turn routes on the map
- **Navigation Options**: Choose between in-app navigation or external apps
- **Route Calculation**: Use OSRM (Open Source Routing Machine) for accurate routing
- **Distance Calculation**: Calculate straight-line and road distances

## How to Use

### 1. Launch OpenStreetMap Activity

```java
Intent intent = new Intent(this, MyOpenStreetMap.class);
intent.putExtra("isRescuerMode", true);
intent.putExtra("latitude", 14.5995);
intent.putExtra("longitude", 120.9842);
intent.putExtra("locationAddress", "Emergency Location, Manila");
intent.putExtra("seniorName", "John Doe");
intent.putExtra("seniorPhone", "+639123456789");
intent.putExtra("emergencyDescription", "Senior needs immediate assistance");
startActivity(intent);
```

### 2. Available Modes

#### Rescuer Mode
- Rescuers view emergency locations
- Get navigation routes to emergency sites
- Call seniors directly
- Track their own location

#### Senior Tracking Mode
- Seniors track approaching rescuers
- View rescuer locations in real-time
- Call closest rescuer
- Receive notifications when rescuers respond

#### Emergency Mode
- Display emergency location for seniors
- Show emergency information
- Basic location display

### 3. Testing

Use the `OpenStreetMapExample` activity to test different modes:

```java
// Launch example activity
Intent intent = new Intent(this, OpenStreetMapExample.class);
startActivity(intent);
```

## Technical Details

### Routing API
- **Service**: OSRM (Open Source Routing Machine)
- **URL**: `https://router.project-osrm.org/route/v1`
- **Features**: Turn-by-turn directions, distance calculation, travel time estimation
- **Cost**: Free (with usage limits)

### Map Tiles
- **Provider**: OpenStreetMap (via osmdroid)
- **Features**: High-quality map tiles, multiple zoom levels
- **Cost**: Free

### Location Services
- **Provider**: Google Play Services Location API
- **Features**: GPS, network-based location, fused location provider
- **Compatibility**: Same as Google Maps implementation

## Migration Steps

### 1. Update Dependencies
The `build.gradle.kts` file has been updated with OpenStreetMap dependencies.

### 2. Update Intent Calls
Replace Google Maps intents with OpenStreetMap intents:

```java
// Old (Google Maps)
Intent intent = new Intent(this, MyGoogleMAp.class);

// New (OpenStreetMap)
Intent intent = new Intent(this, MyOpenStreetMap.class);
```

### 3. Test Functionality
Use the example activity to test all features before deploying.

## Benefits

### ✅ Cost Savings
- **Google Maps**: Requires API key with usage costs
- **OpenStreetMap**: Completely free to use

### ✅ No API Limits
- **Google Maps**: Has daily/monthly usage limits
- **OpenStreetMap**: No strict usage limits

### ✅ Open Source
- **Google Maps**: Proprietary, closed source
- **OpenStreetMap**: Open source, community-driven

### ✅ Privacy
- **Google Maps**: Data sent to Google servers
- **OpenStreetMap**: Local processing, no data sent to external servers

## Limitations

### ⚠️ Map Quality
- OpenStreetMap may have less detailed data in some areas
- Satellite imagery not available (unless using additional providers)

### ⚠️ Routing Accuracy
- OSRM routing may be less accurate than Google Maps in some regions
- Traffic data not available

### ⚠️ Offline Support
- Requires internet connection for map tiles
- Can be mitigated with offline tile caching

## Troubleshooting

### Common Issues

1. **Map not loading**
   - Check internet connection
   - Verify OpenStreetMap permissions in manifest

2. **Location not working**
   - Ensure location permissions are granted
   - Check GPS is enabled

3. **Routing errors**
   - Verify OSRM API is accessible
   - Check coordinates are valid

### Debug Mode
Enable debug logging to troubleshoot issues:

```java
Log.d("MyOpenStreetMap", "Debug message");
```

## Support

For issues or questions about the OpenStreetMap implementation:

1. Check the logs for error messages
2. Verify all permissions are granted
3. Test with the example activity
4. Compare with the original Google Maps implementation

## Future Enhancements

Potential improvements for the OpenStreetMap implementation:

- **Offline Maps**: Cache map tiles for offline use
- **Custom Map Styles**: Implement different map themes
- **Alternative Routing**: Add support for other routing providers
- **Enhanced Markers**: Custom marker icons and animations
- **Map Clustering**: Group nearby markers for better performance
