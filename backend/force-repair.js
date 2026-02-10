const mongoose = require('mongoose');
const dns = require('dns');
require('dotenv').config();

dns.setServers(['8.8.8.8', '8.8.4.4']);

const MONGO_URI = "mongodb+srv://davidartaviarodriguez_db_user:UYUNlKLuR1rSoTsu@kmadb.jodngjz.mongodb.net/kuma_evolve_db?retryWrites=true&w=majority&appName=kmadb";

const AthleteSchema = new mongoose.Schema({
    name: String,
    imageUrl: String
});
const Athlete = mongoose.model('Athlete', AthleteSchema);

async function repairAll() {
    try {
        await mongoose.connect(MONGO_URI);
        const athletes = await Athlete.find();
        console.log(`📊 Inspection of ${athletes.length} athletes...`);

        let repairCount = 0;
        for (let athlete of athletes) {
            console.log(`Checking [${athlete.name}]...`);
            let url = athlete.imageUrl;
            if (url && url.includes('base64,')) {
                if (!url.startsWith('data:image/jpeg;base64,')) {
                    console.log(`⚠️  Invalid prefix found: ${url.substring(0, 40)}...`);
                    const dataPart = url.split('base64,')[1];
                    athlete.imageUrl = `data:image/jpeg;base64,${dataPart}`;
                    await athlete.save();
                    console.log(`✅ Repaired!`);
                    repairCount++;
                } else {
                    console.log(`✅ Prefix OK.`);
                }
            } else {
                console.log(`ℹ️ No image or no base64 found.`);
            }
        }
        console.log(`🏁 Done! Repaired ${repairCount} records.`);
    } catch (err) {
        console.error(err);
    } finally {
        await mongoose.disconnect();
    }
}

repairAll();
