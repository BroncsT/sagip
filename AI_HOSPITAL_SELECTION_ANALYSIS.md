# AI Hospital Selection Analysis

## Executive Summary

The Sagip emergency response system has two hospital selection approaches:

1. **Full AI System** (Implemented but **NOT currently used**)
   - Location: `app/src/main/java/com/example/sagip_prototype/ai/`
   - Files: `EmergencyRoomAI.java`, `TOPSISAlgorithm.java`, `MLRecommendationSystem.java`, `AStarAlgorithm.java`

2. **Simple Heuristic System** (Currently active)
   - Location: `EmergencyAssignmentActivity.java` lines 544-578
   - Used in real emergency responses

---

## Full AI System Architecture (Not Currently Used)

### System Components

#### 1. EmergencyRoomAI (Main Orchestrator)
**File:** `EmergencyRoomAI.java`

**Purpose:** Central AI system that coordinates all hospital selection algorithms

**Key Methods:**
- `selectOptimalHospital()` - Main entry point
  - Gets available hospitals within 15km radius
  - Applies TOPSIS algorithm (40% weight)
  - Applies ML recommendation (60% weight)
  - Combines results using hybrid approach
  - Calculates optimal route using A* algorithm
  - Returns best hospital with confidence score

**Process Flow:**
1. Query hospitals from Firestore
2. Filter hospitals within 15km radius
3. Apply TOPSIS multi-criteria decision analysis
4. Apply ML-based recommendation system
5. Combine scores (TOPSIS 40% + ML 60%)
6. Calculate optimal route with A* algorithm
7. Generate confidence score
8. Return recommendation with alternatives

**Database Fields Used:**
- `hospitalName` / `name`
- `currentLocation` (GeoPoint) / `location`
- `hospitalAddress` / `address`
- `mobileNumber` / `phone`
- `isOperational` (default: true)
- `erStatus` / `emergencyRoomStatus` (default: "available")
- `erBeds` / `totalBeds`
- `availableErBeds` / `availableBeds`

**Strength:** Falls back to Angeles City default location if hospital has no coordinates

---

#### 2. TOPSIS Algorithm
**File:** `TOPSISAlgorithm.java`

**Purpose:** Multi-criteria decision analysis for hospital ranking

**Criteria & Weights:**
- Distance: 20%
- ER Status: 25% (highest weight - most important for emergencies)
- Availability: 15%
- Specialization: 15%
- Response Time: 10%
- Capacity: 8%
- Traffic: 4%
- Weather: 3%

**Algorithm Steps:**
1. Create decision matrix (8 criteria × n hospitals)
2. Normalize matrix using vector normalization
3. Apply criteria weights
4. Determine ideal and negative ideal solutions
5. Calculate separation measures
6. Calculate relative closeness to ideal
7. Rank hospitals by score

**ER Status Scoring:**
- "available" → 100% score
- "busy" → 70% score
- "overcrowded" / "crowded" → 30% score

**Specialization Matching:**
- Maps emergency types to specializations:
  - cardiac_arrest/heart_attack → cardiology
  - stroke/head_injury → neurology
  - trauma/accident → trauma
  - respiratory/breathing → pulmonology
  - pediatric → pediatrics

**Score Calculation:**
```java
distanceScore = 100 - (distance * 10)  // 100 points max, -10 per km
totalScore = (distanceScore * 0.4) + (erScore * 0.6)
```

---

#### 3. ML Recommendation System
**File:** `MLRecommendationSystem.java`

**Purpose:** Machine learning-based hospital recommendation

**Features & Weights:**
- Distance: 25%
- Availability: 20%
- Specialization: 18%
- Response Time: 15%
- Capacity: 10%
- Time of Day: 5%
- Day of Week: 3%
- Weather: 2%
- Traffic: 2%

**ML Score Calculation:**
1. Extract features for each hospital
2. Apply weighted scoring
3. Normalize scores
4. Apply historical success rate (30% weight)
5. Combine: (Current Score * 0.7) + (Historical Rate * 0.3)

**Time-based Intelligence:**
- Morning rush (7-9 AM): 60% score
- Evening rush (5-7 PM): 60% score
- Lunch time (12-2 PM): 70% score
- Night time (10 PM-5 AM): 90% score
- Weekdays: 70% score
- Weekends: 90% score

**Historical Learning:**
- Tracks hospital performance in `hospitalPerformance/historicalData`
- Calculates success rate from last 100 cases
- Updates automatically after each emergency

**Database Collections:**
- `Sagip/hospitalPerformance/historicalData/` - Case history
- `Sagip/hospitalPerformance/hospitalStats/{hospitalId}` - Aggregated stats

---

#### 4. A* Algorithm
**File:** `AStarAlgorithm.java`

**Purpose:** Optimal route planning to selected hospital

**Route Options:**
1. Direct route - shortest path, local/arterial roads
2. Highway route - longer distance but faster (for >5km)
3. Alternative route - different path for flexibility

**Route Scoring (A* Score):**
- Distance: 20%
- Time: 20%
- Traffic: 15%
- Emergency suitability: 15%
- Hospital proximity: 10%
- Accessibility: 10%
- Safety: 5%
- Road quality: 5%

**Real-time Factors:**
- Traffic multiplier based on route type and time
- Weather multiplier (rain 1.3x, fog 1.2x, snow 1.5x)
- Time of day multiplier
- Rush hour adjustments

**Estimated Times:**
- City roads: 30 km/h average
- Highways: 60 km/h average
- Local roads: 25 km/h average

---

### AI System Output

**Return Type:** `AIRecommendationResult`

**Fields:**
- `recommendedHospital` - Best hospital
- `optimalRoute` - Calculated route
- `alternativeHospitals` - Top 3 alternatives
- `confidenceScore` - 0.0 to 1.0
- `message` - Human-readable explanation

**Confidence Levels:**
- High: ≥80%
- Medium: 60-79%
- Low: <60%

---

## Current System (Actually Used)

### Simple Heuristic in EmergencyAssignmentActivity
**File:** `EmergencyAssignmentActivity.java` lines 544-578
**Method:** `calculateHospitalScore()`

**Current Logic:**
```java
private double calculateHospitalScore(HospitalData hospital) {
    double score = 0.0;
    
    // Distance factor - 40% weight
    double distanceScore = Math.max(0, 100 - (hospital.distance * 10));
    score += distanceScore * 0.4;
    
    // ER Status factor - 60% weight
    double erScore = getERStatusScore(hospital.erStatus);
    score += erScore * 0.6;
    
    return score;
}
```

**ER Status Scoring:**
- "available" → 100 points
- "busy" → 70 points
- "overcrowded" / "crowded" → 30 points
- Unknown → 50 points

**Selection:** Simple score comparison - highest score wins

**Limitations:**
- Only considers distance and ER status
- No specialization matching
- No historical performance
- No ML learning
- No route optimization
- No confidence scoring
- No alternatives provided

---

## Comparison

| Feature | AI System | Current System |
|---------|-----------|----------------|
| **Distance** | ✅ TOPSIS + ML | ✅ Simple scoring |
| **ER Status** | ✅ TOPSIS 25% weight | ✅ 60% weight |
| **Specialization** | ✅ Matching algorithm | ❌ Not considered |
| **Historical Data** | ✅ ML learning | ❌ Not used |
| **Route Planning** | ✅ A* algorithm | ❌ Not used |
| **Traffic** | ✅ Real-time factors | ❌ Not considered |
| **Time of Day** | ✅ ML scoring | ❌ Not considered |
| **Weather** | ✅ Impact calculation | ❌ Not considered |
| **Confidence Score** | ✅ Calculated | ❌ Not provided |
| **Alternatives** | ✅ Top 3 provided | ❌ Not provided |
| **Availability** | ✅ Capacity scoring | ❌ Not used |
| **Response Time** | ✅ Historical avg | ❌ Not used |

---

## Issues Identified

### 1. AI System Not Integrated
**Problem:** Complete AI system exists but is never called

**Impact:**
- System doesn't learn from historical cases
- No specialization matching
- Suboptimal hospital selection
- No route optimization
- Waste of implemented features

**Solution:**
Replace simple heuristic in `EmergencyAssignmentActivity.java` with `EmergencyRoomAI` calls

---

### 2. Simple System Limitations

**Missing Features:**
- No specialization matching for cardiac, neurology, trauma cases
- No historical performance consideration
- No real-time traffic analysis
- No route optimization
- No confidence in recommendation
- No alternative options for rescuers

**Real-world Impact:**
- May send cardiac patients to hospital without cardiology
- Doesn't learn which hospitals perform better
- Doesn't account for time-based conditions
- May recommend congested route during rush hour
- Rescuer has no backup options if primary hospital unavailable

---

### 3. Database Integration Issues

**Current State:**
- AI system expects comprehensive hospital data
- Many fields may be missing in actual database
- Field name mismatches handled in code
- Fallback to default Angeles City location if coordinates missing

**Risks:**
- Some hospitals may not be properly evaluated
- Geocoding fallback may be inaccurate
- Missing data affects scoring accuracy

---

## Recommendations

### Priority 1: Integrate AI System
**Action:** Replace simple heuristic with AI system call

**Change in EmergencyAssignmentActivity.java:**
```java
// BEFORE (lines 544-578)
private HospitalData selectOptimalHospital(List<HospitalData> hospitals) {
    // Simple scoring
}

// AFTER
private void selectOptimalHospitalAI(List<Hospital> hospitals, Emergency emergency) {
    EmergencyRoomAI ai = new EmergencyRoomAI(db);
    ai.selectOptimalHospital(
        emergency, 
        rescuerLat, 
        rescuerLng,
        new EmergencyRoomAI.HospitalSelectionCallback() {
            @Override
            public void onResult(AIRecommendationResult result) {
                if (result.recommendedHospital != null) {
                    displayHospitalInfo(result);
                    // Show alternatives
                    if (!result.alternativeHospitals.isEmpty()) {
                        showAlternativeHospitals(result.alternativeHospitals);
                    }
                    // Show confidence
                    if (result.isLowConfidence()) {
                        showLowConfidenceWarning();
                    }
                }
            }
        }
    );
}
```

---

### Priority 2: Enhance Database
**Action:** Ensure all hospital documents have required fields

**Required Fields:**
- `hospitalName` ✅
- `currentLocation` (GeoPoint) ⚠️ Some missing
- `hospitalAddress` ✅
- `mobileNumber` / `phone` ✅
- `erStatus` ⚠️ Verify values
- `erBeds` / `totalBeds` ⚠️ May be missing
- `availableErBeds` / `availableBeds` ⚠️ May be missing
- `specializations` ❌ Not in database
- `emergencyServices` ❌ Not in database

**Action Items:**
1. Add `specializations` array to hospital documents
2. Add `emergencyServices` map to hospital documents
3. Verify all hospitals have `currentLocation` GeoPoint
4. Add `erBeds` and `availableErBeds` fields
5. Ensure `erStatus` is consistently updated

---

### Priority 3: Add ML Learning Loop
**Action:** Implement historical data collection

**Current State:**
- ML system exists but no data being collected
- `updateAIPerformance()` method exists but not called

**Implementation:**
```java
// After emergency completion in markDone()
public void updateAIPerformance() {
    emergencyRoomAI.updateAIPerformance(
        hospitalId,
        emergencyId,
        success,  // Did patient receive proper care?
        responseTime,
        patientSatisfaction  // From feedback
    );
}
```

**Required:**
1. Add feedback mechanism after rescue
2. Track emergency outcomes
3. Update hospital success rates
4. Use for future recommendations

---

### Priority 4: Add Alternative Hospital Display
**Action:** Show rescuer multiple options

**Current:** Only shows one hospital

**Recommended:** Display primary + 2 alternatives
- Rescuer can see why primary was selected
- Can manually override if needed
- Has backup options

---

### Priority 5: Confidence Score UI
**Action:** Show AI confidence to rescuer

**Display:**
- High confidence (≥80%): Green badge "AI Confident"
- Medium confidence (60-79%): Yellow badge "AI Fair"
- Low confidence (<60%): Red badge "AI Uncertain - Verify"

**Action:**
Rescuer should verify low-confidence recommendations manually

---

## Testing Recommendations

### Scenario 1: Cardiac Emergency
**Test:** AI selects hospital with cardiology specialization

**Expected:**
- Hospital A (2km, no cardiology): 65% score
- Hospital B (3km, has cardiology): 85% score ✓
- Hospital B selected despite being further

---

### Scenario 2: Learning Over Time
**Test:** Same hospital has 3 successful cases

**Expected:**
- First case: 70% ML score
- Second case: 75% ML score
- Third case: 80% ML score ✓
- ML system learns from historical data

---

### Scenario 3: Rush Hour
**Test:** Emergency at 8:00 AM on Monday

**Expected:**
- Time of day factor: 60% base score
- Route includes extra time for traffic
- AI compensates with faster hospital selection

---

### Scenario 4: Overcrowded ER
**Test:** Nearest hospital ER is overcrowded

**Expected:**
- Hospital A (1km, overcrowded): 55% score
- Hospital B (2.5km, available): 85% score ✓
- Hospital B selected despite being further

---

## Answer: Which Location is Used?

**Current System (Actually Used):**
- **Senior's location** is used for hospital selection (lines 322, 402, 512)
- Distance calculated from senior to hospital
- Rescuer location NOT considered for selection

**Full AI System (Not Used):**
- **Senior's location** (`emergency.location`) is used for distance calculation in TOPSIS and ML
- **Rescuer location** (`rescuerLat`, `rescuerLng`) is used ONLY for route planning (A* algorithm)
- Distance scored from senior to hospital (not rescuer to hospital)

**Why Senior Location Makes Sense:**
1. Patient needs to go to hospital from their current location
2. Rescuer will pick them up and transport
3. Better to select hospital close to patient rather than rescuer
4. More stable reference point (senior is stationary)

**Potential Issue:**
If rescuer is far from senior, and hospital is between them, the system won't prioritize that hospital. For example:
- Senior at point A
- Rescuer at point B (10km away)
- Hospital at point C (5km from A, 3km from B)
- System will score based on 5km (senior to hospital), not 3km (rescuer to hospital)

---

## Conclusion

The Sagip system has a sophisticated AI hospital selection system that is fully implemented but **not currently being used**. The current system uses a simple 2-factor heuristic (distance + ER status).

**Key Findings:**
1. Full AI system is complete and ready
2. Comprehensive multi-criteria analysis implemented
3. ML learning system available but unconnected
4. Route optimization via A* algorithm available
5. Current system is simplified and suboptimal
6. Integration required to activate AI features

**Immediate Action Required:**
Replace `selectOptimalHospital()` method in `EmergencyAssignmentActivity.java` with `EmergencyRoomAI.selectOptimalHospital()` call to activate the AI system.

**Estimated Impact:**
- Better hospital selection for specialized emergencies
- Improved outcomes through ML learning
- More efficient routing
- Higher rescuer confidence with alternatives
- Better patient outcomes over time

