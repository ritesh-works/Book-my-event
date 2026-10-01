#!/bin/bash

# Burst Testing Script for Seat Reservation Service
# Usage: ./burst.sh http://localhost:8080 OR ./burst.sh https://your-deployed-url.com

BASE_URL="${1:-http://localhost:8080}"
SHOW_ID=""
CONCURRENT_USERS=500
SEATS_PER_SHOW=100
HOT_SEAT="A1"  # Most users will fight for this seat

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}Seat Reservation Burst Test${NC}"
echo -e "${YELLOW}Base URL: $BASE_URL${NC}"
echo -e "${YELLOW}========================================${NC}\n"

# Step 1: Create a show
echo -e "${YELLOW}[1/3] Creating show with $SEATS_PER_SHOW seats...${NC}"

SEATS=$(printf '"A%d",' $(seq 1 $SEATS_PER_SHOW) | sed 's/,$//')

SHOW_RESPONSE=$(curl -s -X POST "$BASE_URL/shows" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"test-show-$(date +%s)\",
    \"seats\": [$SEATS],
    \"price_paise\": 25000,
    \"per_user_limit\": 4
  }")

SHOW_ID=$(echo $SHOW_RESPONSE | jq -r '.id')

if [ -z "$SHOW_ID" ] || [ "$SHOW_ID" == "null" ]; then
  echo -e "${RED}Failed to create show${NC}"
  echo $SHOW_RESPONSE | jq .
  exit 1
fi

echo -e "${GREEN}✓ Show created: $SHOW_ID${NC}\n"

# Step 2: Verify initial state
echo -e "${YELLOW}[2/3] Initial show state:${NC}"
curl -s -X GET "$BASE_URL/shows/$SHOW_ID" | jq '{
  available_count: .available_count,
  held_count: .held_count,
  confirmed_count: .confirmed_count,
  total_seats: (.available_count + .held_count + .confirmed_count)
}'
echo

# Step 3: Run concurrent burst test
echo -e "${YELLOW}[3/3] Launching burst: $CONCURRENT_USERS concurrent users${NC}"
echo -e "${YELLOW}Hot seat: $HOT_SEAT (most contention)${NC}\n"

TEMP_DIR=$(mktemp -d)
RESULTS_FILE="$TEMP_DIR/results.json"
CONFIRMED=0
DECLINED_SEAT_TAKEN=0
DECLINED_LIMIT=0
ERROR_5XX=0

# Function to make a reservation
make_reservation() {
  local user_id="user_$1"
  local key="key_$1_$(date +%s%N)"
  local seat=$HOT_SEAT  # All fight for same hot seat
  
  RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/shows/$SHOW_ID/reserve" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: $user_id" \
    -d "{
      \"seats\": [\"$seat\"],
      \"idempotency_key\": \"$key\"
    }")
  
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | head -n-1)
  
  echo "$HTTP_CODE $user_id $seat" >> "$RESULTS_FILE"
}

export -f make_reservation
export BASE_URL SHOW_ID HOT_SEAT RESULTS_FILE

# Run concurrent requests
seq 1 $CONCURRENT_USERS | xargs -P 20 -I {} bash -c 'make_reservation {}'

# Analyze results
if [ -f "$RESULTS_FILE" ]; then
  echo -e "${YELLOW}Results Summary:${NC}\n"
  
  CONFIRMED=$(grep "^201" "$RESULTS_FILE" | wc -l)
  DECLINED_SEAT_TAKEN=$(grep "^409" "$RESULTS_FILE" | wc -l)
  ERROR_5XX=$(grep "^5" "$RESULTS_FILE" | wc -l)
  
  echo -e "  201 Confirmed:           ${GREEN}$CONFIRMED${NC}"
  echo -e "  409 Seat Taken (conflict): ${YELLOW}$DECLINED_SEAT_TAKEN${NC}"
  echo -e "  5xx Server Errors:       ${RED}$ERROR_5XX${NC}"
  echo
  
  TOTAL=$((CONFIRMED + DECLINED_SEAT_TAKEN + ERROR_5XX))
  echo "  Total Requests: $TOTAL"
  echo
fi

# Step 4: Final state verification
echo -e "${YELLOW}Final show state:${NC}"
FINAL_STATE=$(curl -s -X GET "$BASE_URL/shows/$SHOW_ID" | jq '{
  available_count: .available_count,
  held_count: .held_count,
  confirmed_count: .confirmed_count,
  total_seats: (.available_count + .held_count + .confirmed_count)
}')

echo "$FINAL_STATE" | jq .

# Reconciliation check
RECONCILIATION=$(echo "$FINAL_STATE" | jq '.total_seats')
if [ "$RECONCILIATION" == "$SEATS_PER_SHOW" ]; then
  echo -e "${GREEN}✓ Reconciliation invariant holds: $RECONCILIATION == $SEATS_PER_SHOW${NC}"
else
  echo -e "${RED}✗ Reconciliation FAILED: $RECONCILIATION != $SEATS_PER_SHOW${NC}"
fi

# Cleanup
rm -rf "$TEMP_DIR"

echo -e "\n${YELLOW}========================================${NC}"
echo -e "${YELLOW}Burst test completed${NC}"
echo -e "${YELLOW}========================================${NC}"
