const mongoose = require('mongoose');
const dns = require('dns');
require('dotenv').config();

// DNS fix
dns.setServers(['8.8.8.8', '8.8.4.4']);

const MONGO_URI = "mongodb+srv://davidartaviarodriguez_db_user:UYUNlKLuR1rSoTsu@kmadb.jodngjz.mongodb.net/kuma_evolve_db?retryWrites=true&w=majority&appName=kmadb";

const AthleteSchema = new mongoose.Schema({
    idCard: String,
    name: String,
    imageUrl: String
});
const Athlete = mongoose.model('Athlete', AthleteSchema);

async function verify() {
    try {
        await mongoose.connect(MONGO_URI);
        const latest = await Athlete.findOne({ name: /David Artavia/i }).sort({ _id: -1 });
        if (latest) {
            console.log('✅ Athlete found:', latest.name);
            console.log('🆔 ID Card:', latest.idCard);
            if (latest.imageUrl) {
                console.log('🖼️ Image URL exists (Base64 length):', latest.imageUrl.length);
                console.log('📝 Image prefix:', latest.imageUrl.substring(0, 50));
            } else {
                console.log('❌ Image URL is missing!');
            }
        } else {
            console.log('❌ No athlete found with that name.');
        }
    } catch (err) {
        console.error('Error:', err);
    } finally {
        await mongoose.disconnect();
    }
}

verify();
