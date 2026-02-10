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

async function diagnostic() {
    try {
        await mongoose.connect(MONGO_URI);
        const athletes = await Athlete.find();
        console.log(`📊 TOTAL ATHLETES: ${athletes.length}`);

        athletes.forEach(a => {
            console.log(`--- [${a.name}] ---`);
            if (a.imageUrl) {
                console.log(`📏 Length: ${a.imageUrl.length}`);
                console.log(`📝 Prefix: ${a.imageUrl.substring(0, 100)}`);
            } else {
                console.log('❌ NO IMAGE URL');
            }
        });

    } catch (err) {
        console.error(err);
    } finally {
        await mongoose.disconnect();
    }
}

diagnostic();
