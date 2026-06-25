const fs = require('fs');
const path = require('path');

const logPath = 'C:\\Users\\arhaa\\.gemini\\antigravity-ide\\brain\\02fd1bf0-2e3b-4db0-ba14-53e6186f1e8f\\.system_generated\\logs\\transcript_full.jsonl';

const lines = fs.readFileSync(logPath, 'utf8').split('\n');
console.log('Total lines:', lines.length);

for (let i = lines.length - 1; i >= 0; i--) {
  const line = lines[i];
  if (!line) continue;
  if (line.includes('read_cf_domain_logs') || line.includes('read_paged_keys') || line.includes('billing_history_debug')) {
    try {
      const obj = JSON.parse(line);
      console.log('--- Step Index:', obj.step_index, 'Source:', obj.source, 'Type:', obj.type);
      console.log(obj.content || JSON.stringify(obj.tool_calls || obj.tool_responses));
    } catch (e) {
      console.log('Error parsing line at index', i, e.message);
    }
  }
}
