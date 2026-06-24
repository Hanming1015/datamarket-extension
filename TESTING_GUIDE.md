# End-to-End Testing Guide: Synapse Data Platform

This document provides a step-by-step walkthrough to test the core features of the Synapse Data Platform, specifically focusing on the **Dynamic Pricing Engine**, **Sensitive Field Modifiers**, and **Granular Consent Management**.

Follow these steps exactly to verify that the frontend UI properly captures complex JSON schemas and that the backend Java engine calculates the billing costs accurately.

---

## Phase 1: Data Owner (Setup & Configuration)

First, act as a **Data Owner** to publish a new dataset, set its intricate pricing rules, and establish consent boundaries.

### Step 1: Create a New Dataset
1. Log in or switch your role to a **Data Owner**.
2. Navigate to **Data Management** (or Dataset Management).
3. Click **Add Dataset** and use the following values:
   - **Name:** `Neural Interface Sleep Patterns 2026`
   - **Description:** `Deep brainwave and sleep stage data collected via neural interfaces.`
   - **Category:** `health`
   - **Record Count:** `80000`
   - **Fields Schema (Copy and paste the exact JSON below):**
     ```json
     [
       { "name": "user_id", "sensitive": true, "type": "string" },
       { "name": "age", "sensitive": false, "type": "integer" },
       { "name": "sleep_stages", "sensitive": true, "type": "string" },
       { "name": "heart_rate_variability", "sensitive": false, "type": "integer" },
       { "name": "body_temperature", "sensitive": false, "type": "float" },
       { "name": "neurological_disorders", "sensitive": true, "type": "array" }
     ]
     ```
     *(Notice we have 3 sensitive fields and 3 normal fields)*

### Step 2: Configure Pricing
1. On the dataset list, click the **green dollar icon ($)** next to your newly created dataset.
2. Fill in the pricing engine configuration:
   - **Base Access Fee ($):** `200`
   - **Fee per Field ($):** `10`
   - **Sensitive Field Multiplier:** `2.0`
   - **Purpose Multipliers (JSON):**
     ```json
     {
       "Sleep Research": 0.5,
       "Medical Research": 0.8,
       "Drug Development": 3.0
     }
     ```
   - **Bulk Discounts (JSON):**
     ```json
     {
       "3": 0.10,
       "5": 0.20
     }
     ```
3. Click **Save Changes**.

### Step 3: Create a Consent Rule
1. Navigate to **Consent Management**.
2. Select your new `Neural Interface Sleep Patterns 2026` dataset.
3. Click **Create Rule** and fill in:
   - **Allowed Roles:** Select `University`.
   - **Allowed Purposes:** Select `Sleep Research`.
   - **Allowed Fields:** Select `age`, `sleep_stages`, `heart_rate_variability`, `body_temperature`, `neurological_disorders`.
   - **Denied Fields (Optional):** Select `user_id`.
   - **Valid Until:** Pick a date in the future (e.g., year 2099).
4. Click **Create Rule**.

---

## Phase 2: Data Consumer (Requesting Access)

Now, act as a **Data Consumer** to discover the data and request access based on the rules set above.

### Step 4: Submit an Access Request
1. Switch your user account/role to **Consumer** (ensure the user's role/organization is considered a `University`).
2. Navigate to the **Data Market** page and find `Neural Interface Sleep Patterns 2026`.
3. Click **Apply for Access**.
4. Fill out the application form:
   - **Purpose:** `Sleep Research`
   - **Requested Fields:** Check the following 4 fields:
     - `age`
     - `sleep_stages` (Sensitive)
     - `heart_rate_variability`
     - `body_temperature`
5. Submit the Request.

---

## Phase 3: Verification & Auto-Calculation

Once the request is submitted (and approved if manual approval is required in your current flow), the system will generate a **Billing Record**.

### The Math (Pricing Engine Breakdown):
Let's verify the backend `PricingEngine.class` calculation step-by-step:

1. **Usage Profile:** 
   - Total Fields Requested: 4
   - Normal Fields: 3 (`age`, `heart_rate_variability`, `body_temperature`)
   - Sensitive Fields: 1 (`sleep_stages`)

2. **Calculate Field Base Cost:**
   - Normal Field Cost = 3 * $10 = **$30**
   - Sensitive Field Cost = 1 * ($10 * 2.0x Multiplier) = **$20**
   - *Subtotal = $50*

3. **Apply Bulk Discount:**
   - 4 fields requested triggers the `>= 3` tier.
   - Discount is `0.10` (10% off).
   - Discounted Field Cost = $50 * (1 - 0.10) = **$45**

4. **Add Base Access Fee:**
   - Pre-Purpose Total = $45 (Fields) + $200 (Base Access Fee) = **$245**

5. **Apply Purpose Multiplier:**
   - Purpose is `Sleep Research`, which has a multiplier of `0.5x` (50% off for academic research).
   - **Final Total Amount** = $245 * 0.5 = **$122.50**

### Final Check
Go to the **Billing / Dashboard** (or check your database's `billing_record` table). Ensure that the `cost` for this specific transaction is exactly **`122.50`**. 

If the cost matches exactly, congratulations! The end-to-end flow, from the dynamic JSON UI input to the Spring Boot persistence, and the complex mathematical mapping in the Pricing Engine are all working perfectly in unison.