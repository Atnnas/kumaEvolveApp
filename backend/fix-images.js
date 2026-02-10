const mongoose = require('mongoose');
const dns = require('dns');
require('dotenv').config();

// DNS fix
dns.setServers(['8.8.8.8', '8.8.4.4']);

const MONGO_URI = "mongodb+srv://davidartaviarodriguez_db_user:UYUNlKLuR1rSoTsu@kmadb.jodngjz.mongodb.net/kuma_evolve_db?retryWrites=true&w=majority&appName=kmadb";

const AthleteSchema = new mongoose.Schema({
    imageUrl: String
});
const Athlete = mongoose.model('Athlete', AthleteSchema);

async function repair() {
    try {
        await mongoose.connect(MONGO_URI);
        console.log('📡 Connected to DB for repair...');

        const corruptPrefix = 'data:multipart/form-data;base64,';
        const correctPrefix = 'data:image/jpeg;base64,';

        const athletes = await Athlete.find({ imageUrl: { $regex: '^data:multipart/form-data' } });
        console.log(`🔍 Found ${athletes.length} athletes with corrupt image prefixes.`);

        for (let athlete of athletes) {
            athlete.imageUrl = athlete.imageUrl.replace(corruptPrefix, correctPrefix);
            await athlete.save();
            console.log(`✅ Repaired athlete ID: ${athlete._id}`);
        }

        console.log('🏁 Repair complete.');
    } catch (err) {
        console.error('❌ Error during repair:', err);
    } finally {
        await mongoose.disconnect();
    }
}

repair();
