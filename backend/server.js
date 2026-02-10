const express = require('express');
const mongoose = require('mongoose');
const cors = require('cors');
const dns = require('dns');
require('dotenv').config();

// --- CRITICAL DNS FIX ---
// Force Node.js to use Google DNS for SRV and Hostname resolution
// This has been verified to work in this environment.
dns.setServers(['8.8.8.8', '8.8.4.4']);
console.log("📡 DNS: Forcing Google DNS (8.8.8.8) for SRV resolution.");

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

// Standard MongoDB Atlas SRV Connection String
// Using the one verified in test-write.js
const MONGO_URI = "mongodb+srv://davidartaviarodriguez_db_user:UYUNlKLuR1rSoTsu@kmadb.jodngjz.mongodb.net/kuma_evolve_db?retryWrites=true&w=majority&appName=kmadb";

console.log("🔌 Connecting to MongoDB Atlas...");

mongoose.connect(MONGO_URI, {
    serverSelectionTimeoutMS: 30000,
    connectTimeoutMS: 30000
})
    .then(() => console.log('✅ Conectado a MongoDB Atlas (Cloud Write Guaranteed)'))
    .catch(err => {
        console.error('❌ Error de conexión a MongoDB:', err.message);
    });

// Schemas
const UserSchema = new mongoose.Schema({
    uid: { type: String, unique: true },
    email: String,
    name: String,
    photoUrl: String,
    lastLogin: Date
});
const User = mongoose.model('User', UserSchema);

const AthleteSchema = new mongoose.Schema({
    name: String,
    category: String,
    rank: String,
    imageUrl: String
}, { collection: 'athletes' });
const Athlete = mongoose.model('Athlete', AthleteSchema);

// API Endpoints
app.get('/api/health', (req, res) => {
    res.status(200).json({ status: 'ok', db: mongoose.connection.readyState === 1 ? 'connected' : 'disconnected' });
});

app.post('/api/auth', async (req, res) => {
    try {
        const { uid, email, name, photoUrl } = req.body;
        console.log(`📥 Auth request for: ${email}`);

        const update = {
            uid,
            email,
            name,
            photoUrl,
            lastLogin: new Date()
        };

        const user = await User.findOneAndUpdate(
            { uid: uid },
            update,
            { upsert: true, new: true }
        );

        console.log(`👤 Usuario sincronizado: ${name}`);
        res.status(200).json({ success: true, user });
    } catch (error) {
        console.error('Error en /api/auth:', error);
        res.status(500).json({ success: false, error: error.message });
    }
});

app.get('/api/athletes', async (req, res) => {
    try {
        const athletes = await Athlete.find();
        console.log(`🥋 Enviando ${athletes.length} atletas`);
        res.status(200).json(athletes);
    } catch (error) {
        console.error('Error en /api/athletes:', error);
        res.status(500).json({ success: false, error: error.message });
    }
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`🚀 Servidor corriendo en http://localhost:${PORT}`);
});
