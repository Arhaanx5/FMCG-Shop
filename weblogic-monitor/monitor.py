import os
import sys
import json
import time
import re
import argparse
from datetime import datetime, timedelta
from playwright.sync_api import sync_playwright, TimeoutError

# Load configurations
CONFIG_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "config.json")
SESSION_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "whatsapp_session")

def load_config():
    if not os.path.exists(CONFIG_FILE):
        print(f"Error: Configuration file not found at {CONFIG_FILE}")
        sys.exit(1)
    with open(CONFIG_FILE, 'r') as f:
        return json.load(f)

# Global configuration variable
config = load_config()

def get_time_difference_in_minutes(weblogic_time_str):
    """
    Parses a time string like '6:36 PM' or '18:36' and compares it with system time.
    Returns absolute difference in minutes.
    """
    try:
        # Normalize the time string
        clean_time_str = weblogic_time_str.strip().upper()
        
        # Parse based on AM/PM presence
        if "AM" in clean_time_str or "PM" in clean_time_str:
            parsed_time = datetime.strptime(clean_time_str, "%I:%M %p")
        else:
            parsed_time = datetime.strptime(clean_time_str, "%H:%M")
            
        now = datetime.now()
        web_datetime = now.replace(hour=parsed_time.hour, minute=parsed_time.minute, second=0, microsecond=0)
        
        # Handle day rollover boundary if checking around midnight
        diff = abs(now - web_datetime)
        if diff > timedelta(hours=12):
            # Adjust if parsed time is from previous or next day due to boundary
            if now > web_datetime:
                web_datetime += timedelta(days=1)
            else:
                web_datetime -= timedelta(days=1)
            diff = abs(now - web_datetime)
            
        return diff.total_seconds() / 60.0, web_datetime.strftime("%I:%M %p"), now.strftime("%I:%M %p")
    except Exception as e:
        print(f"[WARN] Failed to parse time string '{weblogic_time_str}': {e}")
        return 0, weblogic_time_str, datetime.now().strftime("%I:%M %p")

def scrape_weblogic_status(page):
    """
    Scrapes WebLogic Summary of Servers page.
    Returns a dictionary with parsed status and extracted console time.
    """
    print("[INFO] Scraping WebLogic server table status...")
    status_report = []
    warning_nodes = []
    
    # 1. Parse Console Time
    console_time_str = None
    try:
        # Search the page text for "as of HH:MM AM/PM" or "as of HH:MM"
        page_content = page.content()
        time_match = re.search(r"as of\s+([0-9]{1,2}:[0-9]{2}(?:\s*(?:AM|PM|am|pm))?)", page_content)
        if time_match:
            console_time_str = time_match.group(1).strip()
            print(f"[INFO] Found WebLogic Console Time: {console_time_str}")
        else:
            # Fallback locator for the system status element
            status_text = page.locator("text='Health of Running Servers as of'").first.text_content(timeout=2000)
            if status_text:
                time_match = re.search(r"as of\s+(.*)", status_text)
                if time_match:
                    console_time_str = time_match.group(1).strip()
                    print(f"[INFO] Sidebar time text matched: {console_time_str}")
    except Exception as e:
        print(f"[WARN] Could not parse console timestamp: {e}")

    # 2. Parse Server Table
    try:
        # Locate the table by common WebLogic classes/headers or just general tables
        table = page.locator("table:has-text('State'):has-text('Health')").first
        if not table.is_visible():
            table = page.locator("table.table-style, table").first # fallback
            
        rows = table.locator("tr").all()
        print(f"[INFO] Found {len(rows)} rows in server table.")
        
        for row in rows:
            text = row.text_content()
            # Skip header rows
            if "Currently Open Sockets" in text or "Name" in text and "State" in text:
                continue
                
            cells = row.locator("td").all()
            if len(cells) >= 4:
                name = cells[0].text_content().strip()
                state = cells[1].text_content().strip()
                health = cells[2].text_content().strip()
                sockets_str = cells[3].text_content().strip()
                
                # Filter out blank rows
                if not name or not state:
                    continue
                
                # Parse sockets count
                try:
                    sockets = int(re.sub(r"\D", "", sockets_str)) if sockets_str else 0
                except ValueError:
                    sockets = 0
                
                node_data = {
                    "name": name,
                    "state": state,
                    "health": health,
                    "sockets": sockets
                }
                status_report.append(node_data)
                
                # Check for warnings
                if health != "OK" or sockets > config["socket_threshold"] or state != "RUNNING":
                    warning_nodes.append(node_data)
                    
    except Exception as e:
        print(f"[ERROR] Failed to parse servers table: {e}")
        
    return {
        "console_time": console_time_str,
        "servers": status_report,
        "warnings": warning_nodes
    }

def send_whatsapp_alert(page, message, screenshot_path=None):
    """
    Automates sending message and optional screenshot to WhatsApp group.
    """
    print(f"[INFO] Attempting to send WhatsApp message to group: '{config['whatsapp_group_name']}'")
    try:
        page.goto("https://web.whatsapp.com/")
        # Wait for chat list to load (Search bar editable)
        search_box_selector = "div[contenteditable='true'][data-tab='3'], div[contenteditable='true'][title='Search or start new chat']"
        page.wait_for_selector(search_box_selector, timeout=30000)
        
        # Click search box and search for group
        search_box = page.locator(search_box_selector).first
        search_box.click()
        search_box.fill("")
        search_box.type(config["whatsapp_group_name"])
        time.sleep(2)
        search_box.press("Enter")
        time.sleep(2)
        
        # Verify chat is loaded
        chat_header = page.locator("header:has-text('" + config["whatsapp_group_name"] + "')").first
        if not chat_header.is_visible():
            # Alternative: Click the specific group name from list
            group_in_list = page.locator(f"span[title='{config['whatsapp_group_name']}']").first
            if group_in_list.is_visible():
                group_in_list.click()
                time.sleep(2)
            else:
                print(f"[ERROR] Could not find WhatsApp group chat '{config['whatsapp_group_name']}'!")
                return False
                
        # Send Text Message
        msg_box_selector = "div[contenteditable='true'][data-tab='10'], div[contenteditable='true'][title='Type a message']"
        page.wait_for_selector(msg_box_selector, timeout=10000)
        msg_box = page.locator(msg_box_selector).first
        msg_box.click()
        
        # Handle multiple lines in message
        lines = message.split('\n')
        for line in lines:
            msg_box.type(line)
            msg_box.press("Shift+Enter")
        msg_box.press("Enter")
        print("[INFO] Message text sent successfully.")
        time.sleep(1)
        
        # Send Screenshot if provided
        if screenshot_path and os.path.exists(screenshot_path):
            print(f"[INFO] Uploading screenshot: {screenshot_path}")
            
            # Click attach button (+)
            attach_btn = page.locator("div[title='Attach'], button[aria-label='Attach']").first
            attach_btn.click()
            time.sleep(1)
            
            # File Input element (Playwright inputs files directly to hidden file inputs)
            file_input = page.locator("input[accept*='image/*']").first
            file_input.set_input_files(screenshot_path)
            
            # Wait for sending preview screen
            send_btn_selector = "span[data-icon='send'], div[aria-label='Send']"
            page.wait_for_selector(send_btn_selector, timeout=15000)
            
            # Click send button
            page.locator(send_btn_selector).first.click()
            print("[INFO] Screenshot sent successfully.")
            time.sleep(3) # Wait for complete upload
            
        return True
    except Exception as e:
        print(f"[ERROR] Failed to send WhatsApp notification: {e}")
        return False

def login_weblogic(page):
    """
    Logs in to WebLogic Administration Console.
    """
    print(f"[INFO] Opening WebLogic Console: {config['weblogic_url']}")
    page.goto(config["weblogic_url"])
    
    # Check if already logged in (redirected to portal or frameset present)
    if "console.portal" in page.url:
        print("[INFO] Already logged into WebLogic console.")
        return True
        
    try:
        # Standard WebLogic login page inputs
        page.wait_for_selector("input[id='j_username'], input[name='j_username']", timeout=10000)
        
        page.locator("input[id='j_username'], input[name='j_username']").first.fill(config["weblogic_user"])
        page.locator("input[id='j_password'], input[name='j_password']").first.fill(config["weblogic_password"])
        
        # Click login button
        login_btn = page.locator("input[type='submit'], button:has-text('Login'), input[value='Login']").first
        login_btn.click()
        
        # Wait for portal page
        page.wait_for_url("**/console.portal*", timeout=20000)
        print("[INFO] Logged in successfully to WebLogic.")
        return True
    except Exception as e:
        print(f"[ERROR] WebLogic login failed: {e}")
        return False

def navigate_to_servers_and_enable_autorefresh(page):
    """
    Navigates directly to Summary of Servers and clicks the native Auto-Refresh circle icon once.
    """
    server_table_url = f"{config['weblogic_url']}/console.portal?_nfpb=true&_pageLabel=CoreServerServerTablePage"
    print(f"[INFO] Navigating to Servers Table: {server_table_url}")
    page.goto(server_table_url)
    
    # Click 🔄 icon once to enable WebLogic Native Auto-Refresh
    try:
        # Locate the refresh icon above Customize this Table (top left circular arrow)
        refresh_icon = page.locator("a:has(img[src*='refresh']), a[title*='Automatically refresh'], a[title*='refresh'], a:has-text('🔄')").first
        if refresh_icon.is_visible():
            refresh_icon.click()
            print("[INFO] Native Auto-Refresh circle (🔄) clicked ONCE successfully.")
            time.sleep(2)
        else:
            print("[WARN] Auto-Refresh circle icon not visible, skipping native click. Live check loop will still run.")
    except Exception as e:
        print(f"[WARN] Failed to click native refresh icon: {e}")

def run_monitor(dry_run=False):
    """
    Main loop function that maintains continuous live monitoring.
    """
    with sync_playwright() as p:
        print("[INFO] Initializing persistent Chromium browser...")
        
        # Keep headed browser if doing dry_run or scanning code. Run headless for background monitoring.
        headless = False if dry_run else True
        
        context = p.chromium.launch_persistent_context(
            user_data_dir=SESSION_DIR,
            headless=headless,
            args=["--start-maximized"]
        )
        
        # Create tabs
        page_weblogic = context.pages[0] if context.pages else context.new_page()
        page_whatsapp = context.new_page() if not dry_run else None
        
        # Log into WebLogic
        if not login_weblogic(page_weblogic):
            print("[FATAL] Could not connect or login to WebLogic. Exiting monitor.")
            return
            
        # Navigate to Servers & Turn on Auto-Refresh
        navigate_to_servers_and_enable_autorefresh(page_weblogic)
        
        # Initialize loop variables
        last_routine_sent_time = time.time()
        active_alerts = {}  # Tracks node_name -> first_alert_timestamp
        stale_alert_triggered = False
        
        print("\n" + "="*50)
        print("[SUCCESS] WebLogic Live Monitoring Daemon Running...")
        print(f"Checking every {config['live_check_interval_seconds']} seconds.")
        print(f"Routine WhatsApp report sent every {config['routine_report_interval_seconds'] // 60} minutes.")
        print("="*50 + "\n")
        
        while True:
            try:
                # 1. Scrape Live status
                status = scrape_weblogic_status(page_weblogic)
                current_time = time.time()
                
                # Check for Login Session Expiry (if table parsing returns no data, we might be logged out)
                if not status["servers"]:
                    print("[WARN] No server data found. Checking if session expired...")
                    if "LoginForm.jsp" in page_weblogic.url or not login_weblogic(page_weblogic):
                        print("[INFO] Re-logging in to WebLogic...")
                        login_weblogic(page_weblogic)
                        navigate_to_servers_and_enable_autorefresh(page_weblogic)
                        continue
                
                # 2. Time Sync / Stale Page Integrity Check
                if status["console_time"]:
                    drift_mins, parsed_web_t, parsed_sys_t = get_time_difference_in_minutes(status["console_time"])
                    print(f"[INFO] Time integrity check: Console Time = {parsed_web_t} | System Time = {parsed_sys_t} | Drift = {drift_mins:.2f} mins")
                    
                    if drift_mins > config["time_diff_limit_minutes"]:
                        print(f"[ALERT] Console is stale! Drift exceeds {config['time_diff_limit_minutes']} minutes!")
                        if not stale_alert_triggered:
                            msg = f"⚠️ *CRITICAL ALERT: WEBLOGIC CONSOLE IS FROZEN/STALE!*\n\n" \
                                  f"The console auto-refresh appears to have stopped working.\n" \
                                  f"• *Web Console Time:* {parsed_web_t}\n" \
                                  f"• *System Time:* {parsed_sys_t}\n" \
                                  f"• *Time Drift:* {drift_mins:.1f} minutes (Limit: {config['time_diff_limit_minutes']} mins)\n\n" \
                                  f"Attempting self-healing reload..."
                            
                            # Capture stale screenshot
                            screenshot_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "stale_error.png")
                            page_weblogic.screenshot(path=screenshot_path)
                            
                            if not dry_run:
                                send_whatsapp_alert(page_whatsapp, msg, screenshot_path)
                            stale_alert_triggered = True
                            
                        # Self-healing attempt: reload and toggle refresh again
                        navigate_to_servers_and_enable_autorefresh(page_weblogic)
                        time.sleep(10)
                        continue
                    else:
                        if stale_alert_triggered:
                            # Stale condition recovered
                            stale_alert_triggered = False
                            recovery_msg = f"✅ *RECOVERED: WEBLOGIC CONSOLE TIME SYNC RESTORED*\n" \
                                           f"The console time has synchronized with local system clock successfully.\n" \
                                           f"• *Current Console Time:* {parsed_web_t}"
                            if not dry_run:
                                send_whatsapp_alert(page_whatsapp, recovery_msg)
                
                # 3. Warning Alert Logic (5-min Wait & Re-check)
                if status["warnings"]:
                    for warn_node in status["warnings"]:
                        node_name = warn_node["name"]
                        
                        # If warning is seen for the first time
                        if node_name not in active_alerts:
                            print(f"[WARN] Node '{node_name}' is unstable! Triggering 5-minute cooldown check...")
                            active_alerts[node_name] = {
                                "first_seen": current_time,
                                "alerted": False
                            }
                        else:
                            # Warning is persistent. Check if 5 minutes have elapsed since first seen
                            elapsed_sec = current_time - active_alerts[node_name]["first_seen"]
                            print(f"[WARN] Node '{node_name}' warning is persistent (Elapsed: {elapsed_sec // 60:.1f} mins)")
                            
                            if elapsed_sec >= config["recheck_delay_seconds"] and not active_alerts[node_name]["alerted"]:
                                # Cooldown passed, warning still exists! Send immediate Alert.
                                print(f"[CRITICAL] Sustained Warning detected for '{node_name}'! Sending WhatsApp Alert.")
                                
                                alert_msg = f"⚠️ *WEBLOGIC CRITICAL ALERT: SYSTEM UNSTABLE*\n\n" \
                                            f"The following node has been warning/critical for over 5 minutes:\n" \
                                            f"• *Node Name:* `{node_name}`\n" \
                                            f"• *State:* `{warn_node['state']}`\n" \
                                            f"• *Health State:* `{warn_node['health']}`\n" \
                                            f"• *Open Sockets:* `{warn_node['sockets']}` (Limit: {config['socket_threshold']})\n\n" \
                                            f"Please check console image attached."
                                
                                # Take alert screenshot
                                alert_screenshot = os.path.join(os.path.dirname(os.path.abspath(__file__)), "alert_warning.png")
                                page_weblogic.screenshot(path=alert_screenshot)
                                
                                if not dry_run:
                                    success = send_whatsapp_alert(page_whatsapp, alert_msg, alert_screenshot)
                                    if success:
                                        active_alerts[node_name]["alerted"] = True
                                else:
                                    print(f"[DRY-RUN] Would have sent alert: {alert_msg}")
                                    active_alerts[node_name]["alerted"] = True
                else:
                    # Clear active alerts if everything returned to normal
                    if active_alerts:
                        for node_name in list(active_alerts.keys()):
                            if active_alerts[node_name]["alerted"]:
                                recovery_msg = f"✅ *RESOLVED: WEBLOGIC HEALTH RESTORED*\n\n" \
                                               f"Node `{node_name}` has returned to perfectly healthy state (OK)."
                                if not dry_run:
                                    send_whatsapp_alert(page_whatsapp, recovery_msg)
                            print(f"[INFO] Node '{node_name}' is healthy again. Alert cleared.")
                            active_alerts.pop(node_name)

                # 4. Routine Hourly Report
                if current_time - last_routine_sent_time >= config["routine_report_interval_seconds"]:
                    print("[INFO] Routine interval reached. Generating hourly status report...")
                    
                    # Generate report text
                    nodes_summary = ""
                    for node in status["servers"]:
                        status_emoji = "✅" if node["health"] == "OK" and node["state"] == "RUNNING" else "❌"
                        nodes_summary += f"{status_emoji} *{node['name']}*: {node['state']} | Sockets: {node['sockets']}\n"
                    
                    routine_msg = f"📊 *WEBLOGIC HOURLY STATUS REPORT*\n\n" \
                                  f"All nodes are monitored live. Current server status summary:\n\n" \
                                  f"{nodes_summary}\n" \
                                  f"• *Console Timestamp:* {status['console_time'] or 'N/A'}\n" \
                                  f"• *System Timestamp:* {datetime.now().strftime('%I:%M %p')}"
                    
                    # Capture high-quality screenshot
                    routine_screenshot = os.path.join(os.path.dirname(os.path.abspath(__file__)), "routine_status.png")
                    page_weblogic.screenshot(path=routine_screenshot)
                    
                    if not dry_run:
                        success = send_whatsapp_alert(page_whatsapp, routine_msg, routine_screenshot)
                        if success:
                            last_routine_sent_time = current_time
                    else:
                        print(f"[DRY-RUN] Would have sent routine report:\n{routine_msg}")
                        last_routine_sent_time = current_time

            except Exception as e:
                print(f"[EXCEPTION ERROR] Loop cycle hit an exception: {e}")
                time.sleep(10)
                
            # If in dry-run mode, we do a single check and exit
            if dry_run:
                print("\n[DRY-RUN COMPLETE] Single live check finished successfully in headed browser.")
                break
                
            # Wait for next live check
            time.sleep(config["live_check_interval_seconds"])

def scan_whatsapp_qr_setup():
    """
    Opens headed browser to let the user login to WhatsApp Web once.
    """
    with sync_playwright() as p:
        print("\n" + "="*60)
        print("[SETUP] Launching browser to scan WhatsApp Web QR code...")
        print("Please scan the QR code on the browser screen within 60 seconds.")
        print("="*60 + "\n")
        
        context = p.chromium.launch_persistent_context(
            user_data_dir=SESSION_DIR,
            headless=False,
            args=["--start-maximized"]
        )
        
        page = context.new_page()
        page.goto("https://web.whatsapp.com/")
        
        # Wait for search box to load which proves user successfully scanned and logged in
        search_box_selector = "div[contenteditable='true'][data-tab='3'], div[contenteditable='true'][title='Search or start new chat']"
        try:
            page.wait_for_selector(search_box_selector, timeout=60000)
            print("\n[SUCCESS] Login verified! WhatsApp Web session saved successfully.")
            print("You can close the browser or press enter to finish.")
            input("Press [Enter] to complete setup...")
        except TimeoutError:
            print("\n[TIMEOUT] QR code was not scanned in time or login failed. Run --setup again.")
        finally:
            context.close()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="WebLogic server live monitor and WhatsApp bot")
    parser.add_argument("--setup", action="store_true", help="Launch WhatsApp QR code setup session")
    parser.add_argument("--dry-run", action="store_true", help="Run a single live WebLogic status scrape in visible browser without WhatsApp alerts")
    
    args = parser.parse_args()
    
    if args.setup:
        scan_whatsapp_qr_setup()
    elif args.dry-run:
        run_monitor(dry_run=True)
    else:
        run_monitor(dry_run=False)
