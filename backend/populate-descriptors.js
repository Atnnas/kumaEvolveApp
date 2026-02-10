const mongoose = require('mongoose');
const FaceService = require('./FaceService');
const dns = require('dns');
require('dotenv').config();

// Forzar DNS de Google para resolver problemas con SRV de MongoDB Atlas en algunos entornos
dns.setServers(['8.8.8.8', '8.8.4.4']);

// Usar cadena de conexión directa para evitar problemas de DNS con SRV
const MONGO_URI = "mongodb://davidartaviarodriguez_db_user:UYUNlKLuR1rSoTsu@ac-4r7x391-shard-00-00.jodngjz.mongodb.net:27017,ac-4r7x391-shard-00-01.jodngjz.mongodb.net:27017,ac-4r7x391-shard-00-02.jodngjz.mongodb.net:27017/kuma_evolve_db?replicaSet=atlas-m4q4v5-shard-0&ssl=true&authSource=admin&retryWrites=true&w=majority";

const AthleteSchema = new mongoose.Schema({
    name: String,
    imageUrl: String,
    faceDescriptor: [Number]
}, { collection: 'athletes' });

const Athlete = mongoose.model('Athlete', AthleteSchema);

async function populate() {
    try {
        await mongoose.connect(MONGO_URI);
        console.log("🔌 Conectado a MongoDB");

        const athletes = await Athlete.find({
            imageUrl: { $exists: true, $ne: null },
            faceDescriptor: null
        });

        console.log(`🔍 Encontrados ${athletes.length} atletas para procesar`);

        for (const athlete of athletes) {
            console.log(`⚙️ Procesando: ${athlete.name}...`);
            try {
                // El imageUrl es Base64 (data:image/jpeg;base64,...)
                if (athlete.imageUrl.startsWith('data:')) {
                    const base64Data = athlete.imageUrl.split(',')[1];
                    const buffer = Buffer.from(base64Data, 'base64');

                    const descriptor = await FaceService.getDescriptor(buffer);

                    if (descriptor) {
                        athlete.faceDescriptor = Array.from(descriptor);
                        await athlete.save();
                        console.log(`✅ Descriptor generado para ${athlete.name}`);
                    } else {
                        console.warn(`⚠️ No se detectó rostro para ${athlete.name}`);
                    }
                }
            } catch (err) {
                console.error(`❌ Error con ${athlete.name}:`, err.message);
            }
        }

        console.log("🏁 Proceso finalizado");
        process.exit(0);
    } catch (error) {
        console.error("❌ Error fatal:", error);
        process.exit(1);
    }
}

populate();
