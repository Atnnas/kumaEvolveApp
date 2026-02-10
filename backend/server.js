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
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ limit: '50mb', extended: true }));

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
    idCard: { type: String, unique: true, required: true },
    name: { type: String, required: true },
    birthDate: { type: Date, required: true },
    grade: String,
    weight: Number,
    imageUrl: String,
    consecutive: { type: Number, unique: true },
    faceDescriptor: { type: [Number], default: null },
    createdAt: { type: Date, default: Date.now }
}, { collection: 'athletes' });

// Pre-save hook to handle auto-consecutive
AthleteSchema.pre('save', async function (next) {
    if (this.isNew) {
        const lastAthlete = await mongoose.model('Athlete').findOne({}, {}, { sort: { 'consecutive': -1 } });
        this.consecutive = lastAthlete && lastAthlete.consecutive ? lastAthlete.consecutive + 1 : 1;
    }
    next();
});

const Athlete = mongoose.model('Athlete', AthleteSchema);

// Import Attendance model
const Attendance = require('./models/Attendance');

// Import Face Service
const FaceService = require('./FaceService');

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

// Helper to clean up corrupt image prefixes (legacy fix)
function cleanImagePrefix(url) {
    if (!url) return url;
    if (url.startsWith('data:multipart/form-data;base64,')) {
        return url.replace('data:multipart/form-data;base64,', 'data:image/jpeg;base64,');
    }
    return url;
}

app.get('/api/athletes', async (req, res) => {
    try {
        let athletes = await Athlete.find().sort({ consecutive: 1 });
        // Auto-repair prefixes for the UI
        athletes = athletes.map(a => {
            const athlete = a.toObject();
            athlete.imageUrl = cleanImagePrefix(athlete.imageUrl);
            return athlete;
        });
        console.log(`漫 Enviando ${athletes.length} atletas (reparados si era necesario)`);
        res.status(200).json(athletes);
    } catch (error) {
        console.error('Error en /api/athletes:', error);
        res.status(500).json({ success: false, error: error.message });
    }
});

const multer = require('multer');
const sharp = require('sharp');
const upload = multer({
    storage: multer.memoryStorage(),
    limits: { fileSize: 10 * 1024 * 1024 } // Limit 10MB
});


// Helper to process and resize images before saving as Base64
async function processImage(file) {
    if (!file) return null;
    try {
        const buffer = await sharp(file.buffer)
            .rotate() // 🥋 Smart Rotation: Auto-applies EXIF orientation
            .resize(1280, 1280, { fit: 'inside', withoutEnlargement: true })
            .jpeg({ quality: 80 })
            .toBuffer();
        const b64 = buffer.toString('base64');
        // Force image/jpeg for consistency across the app
        return `data:image/jpeg;base64,${b64}`;
    } catch (error) {
        console.error('❌ Error in sharp processing:', error);
        // Fallback to original but still use standard prefix if possible
        const originalB64 = file.buffer.toString('base64');
        return `data:image/jpeg;base64,${originalB64}`;
    }
}

app.get('/api/athletes/:id', async (req, res) => {
    try {
        const athlete = await Athlete.findById(req.params.id);
        if (!athlete) return res.status(404).json({ success: false, message: 'No encontrado' });

        const safeAthlete = athlete.toObject();
        safeAthlete.imageUrl = cleanImagePrefix(safeAthlete.imageUrl);

        res.status(200).json(safeAthlete);
    } catch (error) {
        res.status(500).json({ success: false, error: error.message });
    }
});


// Route for athlete registration
app.post('/api/athletes', upload.single('image'), async (req, res) => {
    try {
        console.log('📥 Registration request for athlete:', req.body.name);

        // --- DATA NORMALIZATION ---
        const athleteData = { ...req.body };

        // Explicitly parse dd/MM/yyyy date if it's a string
        if (typeof athleteData.birthDate === 'string' && athleteData.birthDate.includes('/')) {
            const [day, month, year] = athleteData.birthDate.split('/');
            athleteData.birthDate = new Date(year, month - 1, day);
            console.log(`📅 Parsed date: ${athleteData.birthDate.toISOString()}`);
        }

        // Generar descriptor facial si hay imagen
        if (req.file) {
            console.log(`🖼️ Procesando imagen e indexando rostro: ${req.file.originalname}`);
            athleteData.imageUrl = await processImage(req.file);
            const descriptor = await FaceService.getDescriptor(req.file.buffer);
            if (descriptor) {
                athleteData.faceDescriptor = Array.from(descriptor);
                console.log('🧬 Descriptor facial generado correctamente');
            }
        }

        const newAthlete = new Athlete(athleteData);
        await newAthlete.save();
        console.log(`✅ Atleta registrado: ${newAthlete.name} (#${newAthlete.consecutive})`);
        res.status(201).json({ success: true, athlete: newAthlete });
    } catch (error) {
        console.error('❌ Error en POST /api/athletes:', error);
        // Special handling for duplicate ID
        if (error.code === 11000) {
            return res.status(400).json({ success: false, error: "La cédula ya está registrada." });
        }
        res.status(400).json({ success: false, error: error.message });
    }
});

app.put('/api/athletes/:id', upload.single('image'), async (req, res) => {
    try {
        const { id } = req.params;
        console.log(`🔄 Update request for athlete ID: ${id}`);

        const updateData = { ...req.body };

        // Explicitly parse dd/MM/yyyy date
        if (typeof updateData.birthDate === 'string' && updateData.birthDate.includes('/')) {
            const [day, month, year] = updateData.birthDate.split('/');
            updateData.birthDate = new Date(year, month - 1, day);
        }

        // If file is uploaded, update imageUrl and refresh faceDescriptor
        if (req.file) {
            console.log(`🖼️ Actualizando imagen e indexando rostro para: ${updateData.name || id}`);
            updateData.imageUrl = await processImage(req.file);
            const descriptor = await FaceService.getDescriptor(req.file.buffer);
            if (descriptor) {
                updateData.faceDescriptor = Array.from(descriptor);
                console.log('🧬 Descriptor facial actualizado');
            }
        }

        const updatedAthlete = await Athlete.findByIdAndUpdate(id, updateData, { new: true });
        if (!updatedAthlete) return res.status(404).json({ success: false, message: 'Atleta no encontrado' });
        console.log(`✅ Atleta actualizado: ${updatedAthlete.name}`);
        res.status(200).json({ success: true, athlete: updatedAthlete });
    } catch (error) {
        console.error('❌ Error en PUT /api/athletes:', error);
        res.status(400).json({ success: false, error: error.message });
    }
});

app.delete('/api/athletes/:id', async (req, res) => {
    try {
        const { id } = req.params;
        console.log(`🗑️ Delete request for athlete ID: ${id}`);
        const deletedAthlete = await Athlete.findByIdAndDelete(id);
        if (!deletedAthlete) return res.status(404).json({ success: false, message: 'Atleta no encontrado' });
        console.log(`✅ Atleta borrado: ${deletedAthlete.name}`);
        res.status(200).json({ success: true, message: 'Atleta eliminado correctamente' });
    } catch (error) {
        console.error('Error en DELETE /api/athletes:', error);
        res.status(400).json({ success: false, error: error.message });
    }
});

app.post('/api/athletes/delete-multiple', async (req, res) => {
    try {
        const { ids } = req.body;
        console.log(`🗑️ Bulk delete request for ${ids.length} athletes`);
        await Athlete.deleteMany({ _id: { $in: ids } });
        console.log('✅ Borrado masivo completado');
        res.status(200).json({ success: true, message: 'Atletas eliminados correctamente' });
    } catch (error) {
        console.error('Error en POST /api/athletes/delete-multiple:', error);
        res.status(400).json({ success: false, error: error.message });
    }
});

// ========================================
// ATTENDANCE ENDPOINTS
// ========================================

// POST /api/attendance - Registrar nueva asistencia
app.post('/api/attendance', upload.single('image'), async (req, res) => {
    try {
        console.log('📋 Nueva asistencia recibida');

        const { athleteId, studentName, registrationMode, recognitionConfidence } = req.body;

        // Obtener el número de asistencia del día
        const startOfDay = new Date();
        startOfDay.setHours(0, 0, 0, 0);
        const endOfDay = new Date();
        endOfDay.setHours(23, 59, 59, 999);

        const todayCount = await Attendance.countDocuments({
            timestamp: { $gte: startOfDay, $lte: endOfDay }
        });

        const attendanceNumber = todayCount + 1;

        // Procesar imagen
        let photoUrl = null;
        if (req.file) {
            photoUrl = await processImage(req.file);
        }

        // Determinar si es visitante
        const isVisitor = !athleteId || athleteId === 'null';

        const attendanceData = {
            attendanceNumber,
            timestamp: new Date(),
            athleteRef: isVisitor ? null : athleteId,
            studentName: studentName || 'Visitante',
            photoUrl,
            isVisitor,
            recognitionConfidence: recognitionConfidence ? parseFloat(recognitionConfidence) : null,
            registrationMode: registrationMode || 'manual'
        };

        const attendance = new Attendance(attendanceData);
        await attendance.save();

        console.log(`✅ Asistencia #${attendanceNumber} registrada: ${studentName}`);
        res.status(201).json({ success: true, attendance });
    } catch (error) {
        console.error('❌ Error en POST /api/attendance:', error);
        res.status(400).json({ success: false, error: error.message });
    }
});

// GET /api/attendance - Listar asistencias con filtros
app.get('/api/attendance', async (req, res) => {
    try {
        const { from, to, athleteId } = req.query;

        const filter = {};

        // Filtro de fecha
        if (from || to) {
            filter.timestamp = {};
            if (from) filter.timestamp.$gte = new Date(from);
            if (to) {
                const endDate = new Date(to);
                endDate.setHours(23, 59, 59, 999);
                filter.timestamp.$lte = endDate;
            }
        }

        // Filtro de atleta
        if (athleteId && athleteId !== 'all') {
            filter.athleteRef = athleteId;
        }

        const attendances = await Attendance
            .find(filter)
            .populate('athleteRef', 'name idCard')
            .sort({ timestamp: -1 })
            .limit(500);

        console.log(`📊 Consulta de asistencia: ${attendances.length} registros`);
        res.status(200).json(attendances);
    } catch (error) {
        console.error('Error en GET /api/attendance:', error);
        res.status(400).json({ success: false, error: error.message });
    }
});

// PUT /api/attendance/:id - Editar nombre de visitante
app.put('/api/attendance/:id', async (req, res) => {
    try {
        const { id } = req.params;
        const { studentName } = req.body;

        const attendance = await Attendance.findById(id);
        if (!attendance) {
            return res.status(404).json({ success: false, message: 'Asistencia no encontrada' });
        }

        // Agregar al historial de ediciones
        attendance.editHistory.push({
            previousName: attendance.studentName,
            editedBy: 'admin', // TODO: obtener de sesión
            editedAt: new Date()
        });

        attendance.studentName = studentName;
        await attendance.save();

        console.log(`✏️ Asistencia actualizada: ${attendance.studentName}`);
        res.status(200).json({ success: true, attendance });
    } catch (error) {
        console.error('Error en PUT /api/attendance:', error);
        res.status(400).json({ success: false, error: error.message });
    }
});

// DELETE /api/attendance/:id - Eliminar asistencia
app.delete('/api/attendance/:id', async (req, res) => {
    try {
        const { id } = req.params;
        const deleted = await Attendance.findByIdAndDelete(id);

        if (!deleted) {
            return res.status(404).json({ success: false, message: 'Asistencia no encontrada' });
        }

        console.log(`🗑️ Asistencia eliminada: ${deleted.studentName}`);
        res.status(200).json({ success: true, message: 'Asistencia eliminada' });
    } catch (error) {
        console.error('Error en DELETE /api/attendance:', error);
        res.status(400).json({ success: false, error: error.message });
    }
});

// POST /api/attendance/recognize - Reconocer atleta por foto
app.post('/api/attendance/recognize', upload.single('image'), async (req, res) => {
    try {
        if (!req.file) {
            return res.status(400).json({ success: false, message: 'No se recibió imagen' });
        }

        console.log('🔍 Iniciando proceso de reconocimiento facial...');
        const capturedDescriptor = await FaceService.getDescriptor(req.file.buffer);

        if (!capturedDescriptor) {
            console.log('⚠️ No se detectó rostro en la imagen capturada');
            return res.status(200).json({ success: false, message: 'No se detectó ningún rostro' });
        }

        // Obtener descriptores de todos los atletas que los tengan
        const athletes = await Athlete.find({ faceDescriptor: { $ne: null } }).select('name faceDescriptor');

        if (athletes.length === 0) {
            console.log('⚠️ No hay atletas con descriptores registrados');
            return res.status(200).json({ success: false, message: 'No hay base de datos facial registrada' });
        }

        const athleteDescriptors = athletes.map(a => ({
            id: a._id,
            name: a.name,
            descriptor: a.faceDescriptor
        }));

        const match = FaceService.findBestMatch(capturedDescriptor, athleteDescriptors);

        if (!match) {
            console.log('👤 Atleta no reconocido (Visitante)');
            return res.status(200).json({ success: true, recognized: false });
        }

        console.log(`✅ Atleta reconocido: ${match.name} (${match.confidence}%)`);
        res.status(200).json({
            success: true,
            recognized: true,
            athleteId: match.athleteId,
            name: match.name,
            confidence: match.confidence
        });

    } catch (error) {
        console.error('❌ Error en /api/attendance/recognize:', error);
        res.status(500).json({ success: false, error: error.message });
    }
});

app.listen(PORT, '0.0.0.0', async () => {
    console.log(`🚀 Servidor corriendo en http://localhost:${PORT}`);

    // Auto-poblar descriptores faciales faltantes al iniciar
    try {
        console.log("⚙️ Verificando descriptores faciales de atletas...");
        const athletesMissing = await Athlete.find({ faceDescriptor: null, imageUrl: { $ne: null } });
        if (athletesMissing.length > 0) {
            console.log(`🔍 Encontrados ${athletesMissing.length} atletas sin descriptores. Procesando...`);
            for (const athlete of athletesMissing) {
                try {
                    const base64Data = athlete.imageUrl.split(',')[1];
                    if (base64Data) {
                        const buffer = Buffer.from(base64Data, 'base64');
                        const descriptor = await FaceService.getDescriptor(buffer);
                        if (descriptor) {
                            athlete.faceDescriptor = Array.from(descriptor);
                            await athlete.save();
                            console.log(`✅ Descriptor generado para: ${athlete.name}`);
                        }
                    }
                } catch (e) {
                    console.error(`❌ Error procesando ${athlete.name}:`, e.message);
                }
            }
        } else {
            console.log("✅ Todos los atletas tienen descriptores faciales.");
        }
    } catch (err) {
        console.error("❌ Error en auto-población:", err.message);
    }
});
