const { execSync } = require('child_process');

async function testFetch() {
  try {
    console.log("Testing fetch to Groq...");
    const response = await fetch("https://api.groq.com/openai/v1/models");
    console.log("Response status:", response.status);
    const data = await response.json();
    console.log("Successfully fetched models list.");
  } catch (err) {
    console.error("Fetch failed with error:", err.message);
    if (err.cause) {
      console.error("Cause:", err.cause);
    }
  }
}

testFetch();
