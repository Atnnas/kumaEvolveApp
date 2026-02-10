const tf = require('@tensorflow/tfjs');
const faceapi = require('face-api.js');
const path = require('path');
const fs = require('fs');
const sharp = require('sharp');
const fetch = require('node-fetch');

// Mocking required browser environment for face-api.js (stable legacy version)
faceapi.env.monkeyPatch({
    fetch: fetch,
    Canvas: class { getContext() { return null; } },
    Image: class { },
    ImageData: class {
        constructor(data, width, height) {
            this.data = data;
            this.width = width;
            this.height = height;
        }
    }
});

const MODEL_PATH = path.join(__dirname, 'models');

let isLoaded = false;

async function initFaceApi() {
    if (isLoaded) return;

    console.log("🧠 Cargando modelos (face-api.js estable)...");
    try {
        await faceapi.nets.tinyFaceDetector.loadFromDisk(MODEL_PATH);
        await faceapi.nets.faceLandmark68Net.loadFromDisk(MODEL_PATH);
        await faceapi.nets.faceRecognitionNet.loadFromDisk(MODEL_PATH);
        isLoaded = true;
        console.log("✅ Modelos cargados");
    } catch (error) {
        console.error("❌ Error cargando modelos:", error);
    }
}

async function bufferToTensor(buffer) {
    const { data, info } = await sharp(buffer)
        .removeAlpha()
        .resize(640, 480, { fit: 'inside' }) // Aumentada resolución para mayor precisión
        .raw()
        .toBuffer({ resolveWithObject: true });

    return tf.tensor3d(new Uint8Array(data), [info.height, info.width, 3]);
}

async function getDescriptor(imageBuffer) {
    await initFaceApi();

    let tensor;
    try {
        tensor = await bufferToTensor(imageBuffer);

        const detection = await faceapi
            .detectSingleFace(tensor, new faceapi.TinyFaceDetectorOptions())
            .withFaceLandmarks()
            .withFaceDescriptor();

        if (!detection) return null;
        return Array.from(detection.descriptor);
    } catch (err) {
        console.error("❌ Error procesando descriptor:", err);
        return null;
    } finally {
        if (tensor) tf.dispose(tensor);
    }
}

function findBestMatch(capturedDescriptor, athleteDescriptors) {
    if (!capturedDescriptor || !athleteDescriptors.length) return null;

    const faceMatcher = new faceapi.FaceMatcher(
        athleteDescriptors.map(a => new faceapi.LabeledFaceDescriptors(
            a.id.toString(),
            [new Float32Array(a.descriptor)]
        )),
        0.6
    );

    const bestMatch = faceMatcher.findBestMatch(new Float32Array(capturedDescriptor));
    if (bestMatch.label === 'unknown') return null;

    const matchedAthlete = athleteDescriptors.find(a => a.id.toString() === bestMatch.label);
    return {
        athleteId: matchedAthlete.id,
        name: matchedAthlete.name,
        distance: bestMatch.distance,
        confidence: Math.round((1 - bestMatch.distance) * 100)
    };
}

module.exports = {
    initFaceApi,
    getDescriptor,
    findBestMatch
};
