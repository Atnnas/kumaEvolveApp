const mongoose = require('mongoose');

const AttendanceSchema = new mongoose.Schema({
    dailySequence: {
        type: Number,
        required: true
    },
    timestamp: {
        type: Date,
        required: true,
        default: Date.now
    },
    athleteRef: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'Athlete',
        default: null
    },
    studentName: {
        type: String,
        required: true
    },
    evidencePhotoUrl: {
        type: String,
        required: true
    },
    isVisitor: {
        type: Boolean,
        default: false
    },
    recognitionConfidence: {
        type: Number,
        min: 0,
        max: 100,
        default: null
    },
    registrationMode: {
        type: String,
        enum: ['facial', 'manual'],
        required: true
    },
    editHistory: [{
        editedAt: { type: Date, default: Date.now },
        previousName: String,
        editedBy: String
    }]
}, {
    timestamps: true
});

// Índices para mejorar performance de queries
AttendanceSchema.index({ timestamp: -1 });
AttendanceSchema.index({ athleteRef: 1 });
AttendanceSchema.index({ dailySequence: 1, timestamp: -1 });

module.exports = mongoose.model('Attendance', AttendanceSchema);
