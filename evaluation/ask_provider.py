import json
import requests
import sys

def call_api(prompt, options, context):
    """
    Promptfoo custom provider that calls the Sage POST /ask SSE streaming endpoint,
    collects the final Knowledge Card payload, and returns it.
    """
    url = "http://localhost:8080/ask"
    headers = {
        "Content-Type": "application/json",
        "X-Correlation-ID": f"promptfoo-{context.get('vars', {}).get('query_id', 'eval')}"
    }
    payload = {"query": prompt}

    try:
        # Stream request response
        response = requests.post(url, headers=headers, json=payload, stream=True)
        if response.status_code != 200:
            return {
                "error": f"HTTP {response.status_code}: {response.text}"
            }

        knowledge_card = ""
        for line in response.iter_lines():
            if line:
                decoded_line = line.decode('utf-8')
                if decoded_line.startswith("data:"):
                    data_content = decoded_line[5:].strip()
                    # Check if it looks like the final Knowledge Card JSON
                    if "directAnswer" in data_content or "gapFlag" in data_content:
                        knowledge_card = data_content

        if knowledge_card:
            return {
                "output": knowledge_card
            }
        else:
            return {
                "error": "No knowledge card result found in the SSE stream response."
            }

    except Exception as e:
        return {
            "error": f"Exception occurred: {str(e)}"
        }
