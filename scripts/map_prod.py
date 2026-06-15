import asyncio
import os
from playwright.async_api import async_playwright

BASE_URL = "https://keeply.app.br/prod"
EMAIL = "angelolealpl14@gmail.com"
PASSWORD = "05dcf4eead31c148a06d5097d8856d4e"
OUT_DIR = os.path.join(os.path.dirname(__file__), "screenshots")
os.makedirs(OUT_DIR, exist_ok=True)

PUBLIC_ROUTES = [
    ("landing",       "/landing"),
    ("login",         "/login"),
    ("register",      "/register"),
    ("download",      "/download"),
    ("roadmap",       "/roadmap"),
    ("open-source",   "/open-source"),
    ("documentacao",  "/documentacao"),
]

PROTECTED_ROUTES = [
    ("dashboard",           "/dashboard"),
    ("machines",            "/dashboard/machines"),
    ("activities",          "/dashboard/activities"),
    ("protection",          "/dashboard/protection"),
]

async def screenshot(page, name):
    path = os.path.join(OUT_DIR, f"{name}.png")
    await page.screenshot(path=path, full_page=True)
    print(f"  -> saved: {path}")

async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        ctx = await browser.new_context(viewport={"width": 1440, "height": 900})
        page = await ctx.new_page()

        # --- Public routes ---
        print("\n[PUBLIC ROUTES]")
        for name, route in PUBLIC_ROUTES:
            url = BASE_URL + route
            print(f"Visiting {url}")
            try:
                await page.goto(url, wait_until="networkidle", timeout=20000)
                await page.wait_for_timeout(1500)
                await screenshot(page, f"public_{name}")
            except Exception as e:
                print(f"  ERROR: {e}")

        # --- Login ---
        print("\n[LOGIN]")
        await page.goto(BASE_URL + "/login", wait_until="networkidle", timeout=20000)
        await page.wait_for_timeout(1000)
        await screenshot(page, "auth_login_before")

        try:
            await page.fill('input[type="email"], input[name="email"], input[placeholder*="email" i]', EMAIL)
            await page.fill('input[type="password"], input[name="password"]', PASSWORD)
            await screenshot(page, "auth_login_filled")
            await page.click('button[type="submit"], button:has-text("Entrar"), button:has-text("Login")')
            await page.wait_for_url("**/dashboard**", timeout=15000)
            print("  Login successful!")
            await page.wait_for_timeout(2000)
            await screenshot(page, "auth_login_success")
        except Exception as e:
            print(f"  Login error: {e}")
            await screenshot(page, "auth_login_error")

        # --- Protected routes ---
        print("\n[PROTECTED ROUTES]")
        for name, route in PROTECTED_ROUTES:
            url = BASE_URL + route
            print(f"Visiting {url}")
            try:
                await page.goto(url, wait_until="networkidle", timeout=20000)
                await page.wait_for_timeout(2500)
                await screenshot(page, f"app_{name}")
            except Exception as e:
                print(f"  ERROR: {e}")

        # --- Machines detail panel ---
        print("\n[MACHINES - detail interaction]")
        try:
            await page.goto(BASE_URL + "/dashboard/machines", wait_until="networkidle", timeout=20000)
            await page.wait_for_timeout(2000)
            first_row = page.locator("table tbody tr, [data-testid='machine-row'], .machine-item").first
            if await first_row.count() > 0:
                await first_row.click()
                await page.wait_for_timeout(1500)
                await screenshot(page, "app_machines_detail")

                # try Plan tab
                plan_tab = page.get_by_text("Plan", exact=False)
                if await plan_tab.count() > 0:
                    await plan_tab.first.click()
                    await page.wait_for_timeout(1000)
                    await screenshot(page, "app_machines_plan_tab")

                # try Snapshots tab
                snap_tab = page.get_by_text("Snapshots", exact=False)
                if await snap_tab.count() > 0:
                    await snap_tab.first.click()
                    await page.wait_for_timeout(1000)
                    await screenshot(page, "app_machines_snapshots_tab")
        except Exception as e:
            print(f"  ERROR: {e}")

        # --- AI Assistant (if present) ---
        print("\n[AI ASSISTANT - if exists]")
        try:
            await page.goto(BASE_URL + "/dashboard", wait_until="networkidle", timeout=20000)
            await page.wait_for_timeout(1500)
            ai_btn = page.get_by_text("IA", exact=False).or_(page.get_by_text("AI", exact=False)).first
            if await ai_btn.count() > 0:
                await ai_btn.click()
                await page.wait_for_timeout(1500)
                await screenshot(page, "app_ai_assistant")
        except Exception as e:
            print(f"  AI panel: {e}")

        await browser.close()
        print(f"\nDone! Screenshots saved to: {OUT_DIR}")

asyncio.run(main())
