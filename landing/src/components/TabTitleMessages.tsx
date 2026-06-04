"use client";

import { useEffect } from "react";

const DEFAULT_TITLE = "Keeply - Backup e restore por agentes";
const AWAY_TITLES = [
  "Ei, faz seu backup kkkk",
  "Seu restore agradece depois",
  "Volta aqui antes do disco chorar",
];

export function TabTitleMessages() {
  useEffect(() => {
    let messageIndex = 0;
    let intervalId: number | undefined;

    const showDefaultTitle = () => {
      document.title = DEFAULT_TITLE;
      if (intervalId) {
        window.clearInterval(intervalId);
        intervalId = undefined;
      }
    };

    const showAwayTitle = () => {
      document.title = AWAY_TITLES[messageIndex % AWAY_TITLES.length];
      messageIndex += 1;
    };

    const handleVisibilityChange = () => {
      if (document.hidden) {
        showAwayTitle();
        intervalId = window.setInterval(showAwayTitle, 2400);
        return;
      }

      showDefaultTitle();
    };

    document.addEventListener("visibilitychange", handleVisibilityChange);
    showDefaultTitle();

    return () => {
      document.removeEventListener("visibilitychange", handleVisibilityChange);
      if (intervalId) {
        window.clearInterval(intervalId);
      }
    };
  }, []);

  return null;
}
