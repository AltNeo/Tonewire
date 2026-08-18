const DEFAULT_USB_LEVELS = [4, 2, -1, 3, 1];
const DEFAULT_SPEAKER_LEVELS = [-2, 0, 3, 4, 2];

const state = {
    enabled: true,
    route: "usb",
    levels: [...DEFAULT_USB_LEVELS],
    previousLevels: [0, 0, 0, 0, 0],
};

const sliders = [...document.querySelectorAll(".band input")];
const curveLine = document.querySelector("#curve-line");
const curveArea = document.querySelector("#curve-area");
const phoneRouteName = document.querySelector("#phone-route-name");
const tvRouteName = document.querySelector("#tv-route-name");
const presetTitle = document.querySelector("#preset-title");
const tvPresetName = document.querySelector("#tv-preset-name");
const miniBars = [...document.querySelectorAll("#tv-mini-eq i")];
const liveNote = document.querySelector("#live-note");
const powerToggle = document.querySelector("#power-toggle");
const equalizer = document.querySelector("#equalizer");
const toast = document.querySelector("#toast");
let toastTimer;

function formatDb(value) {
    const numericValue = Number(value);
    return numericValue > 0 ? `+${numericValue}` : `${numericValue}`;
}

function rangeFill(value) {
    return `${((Number(value) + 15) / 30) * 100}%`;
}

function curvePath(levels) {
    const width = 320;
    const baseline = 43;
    const scale = 2.15;
    const points = levels.map((level, index) => ({
        x: (width / (levels.length - 1)) * index,
        y: baseline - level * scale,
    }));

    let path = `M${points[0].x} ${points[0].y}`;
    for (let index = 0; index < points.length - 1; index += 1) {
        const current = points[index];
        const next = points[index + 1];
        const midpoint = (current.x + next.x) / 2;
        path += ` C${midpoint} ${current.y} ${midpoint} ${next.y} ${next.x} ${next.y}`;
    }
    return path;
}

function showToast(message) {
    clearTimeout(toastTimer);
    toast.textContent = message;
    toast.classList.add("show");
    toastTimer = setTimeout(() => toast.classList.remove("show"), 1600);
}

function flashLiveStatus() {
    liveNote.classList.remove("flash");
    window.requestAnimationFrame(() => liveNote.classList.add("flash"));
    window.setTimeout(() => liveNote.classList.remove("flash"), 380);
}

function renderEqualizer() {
    sliders.forEach((slider, index) => {
        slider.value = state.levels[index];
        slider.style.setProperty("--range-fill", rangeFill(state.levels[index]));
        slider.closest(".band").querySelector("output").textContent = formatDb(state.levels[index]);
        miniBars[index].style.height = `${34 + ((state.levels[index] + 15) / 30) * 54}%`;
    });

    const path = curvePath(state.enabled ? state.levels : [0, 0, 0, 0, 0]);
    curveLine.setAttribute("d", path);
    curveArea.setAttribute("d", `${path} L320 86 L0 86Z`);
    equalizer.classList.toggle("disabled", !state.enabled);
    powerToggle.classList.toggle("active", state.enabled);
    powerToggle.setAttribute("aria-checked", String(state.enabled));
}

function setRoute(route) {
    state.route = route;
    const isUsb = route === "usb";
    state.levels = [...(isUsb ? DEFAULT_USB_LEVELS : DEFAULT_SPEAKER_LEVELS)];
    const phoneName = isUsb ? "USB · Bluetooth dongle" : "Built-in · TV speakers";
    const tvName = isUsb ? "Bluetooth dongle" : "TV speakers";
    const preset = isUsb ? "Cinema warmth" : "Clear dialogue";

    phoneRouteName.textContent = phoneName;
    tvRouteName.textContent = tvName;
    presetTitle.textContent = preset;
    tvPresetName.textContent = preset;

    document.querySelectorAll("[data-route-card]").forEach((card) => {
        const selected = card.dataset.routeCard === route;
        card.classList.toggle("selected", selected);
        let pill = card.querySelector(".active-pill");
        if (selected && !pill) {
            pill = document.createElement("span");
            pill.className = "active-pill";
            pill.textContent = "ACTIVE";
            card.querySelector(".device-card-top").append(pill);
        } else if (!selected && pill) {
            pill.remove();
        }
    });

    renderEqualizer();
    flashLiveStatus();
    showToast(`${preset} recalled for ${tvName}`);
}

sliders.forEach((slider, index) => {
    slider.style.setProperty("--range-fill", rangeFill(slider.value));
    slider.addEventListener("input", () => {
        state.levels[index] = Number(slider.value);
        renderEqualizer();
        flashLiveStatus();
    });
});

powerToggle.addEventListener("click", () => {
    state.enabled = !state.enabled;
    renderEqualizer();
    showToast(state.enabled ? "Equalizer enabled" : "Equalizer bypassed");
});

document.querySelector("#reset-button").addEventListener("click", () => {
    state.previousLevels = [...state.levels];
    state.levels = [0, 0, 0, 0, 0];
    renderEqualizer();
    flashLiveStatus();
    showToast("Curve reset to flat");
});

document.querySelector("#ab-button").addEventListener("click", () => {
    const current = [...state.levels];
    state.levels = [...state.previousLevels];
    state.previousLevels = current;
    renderEqualizer();
    flashLiveStatus();
    showToast("A/B curve swapped");
});

document.querySelector("#output-card").addEventListener("click", () => {
    setRoute(state.route === "usb" ? "speaker" : "usb");
});

document.querySelectorAll("[data-route-card]").forEach((card) => {
    card.addEventListener("click", () => setRoute(card.dataset.routeCard));
});

document.querySelectorAll("[data-nav]").forEach((button) => {
    button.addEventListener("click", () => {
        const target = button.dataset.nav;
        document.querySelectorAll("[data-nav]").forEach((item) => {
            item.classList.toggle("active", item === button);
        });
        document.querySelectorAll(".phone-view").forEach((view) => {
            view.classList.toggle("active", view.dataset.view === target);
        });
    });
});

document.querySelectorAll(".stage-tab").forEach((tab) => {
    tab.addEventListener("click", () => {
        const target = tab.dataset.stage;
        document.querySelectorAll(".stage-tab").forEach((item) => {
            const selected = item === tab;
            item.classList.toggle("active", selected);
            item.setAttribute("aria-selected", String(selected));
        });
        document.querySelectorAll("[data-tv-view]").forEach((view) => {
            view.classList.toggle("active", view.dataset.tvView === target);
        });
        if (target === "pair") {
            showToast("TV setup screen selected");
        }
    });
});

document.querySelectorAll(".automation-row .power-toggle").forEach((toggle) => {
    toggle.addEventListener("click", () => {
        const active = toggle.classList.toggle("active");
        toggle.setAttribute("aria-checked", String(active));
        showToast(active ? "Automatic route presets enabled" : "Automatic route presets disabled");
    });
});

renderEqualizer();
