package com.example.smartintersections;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class SpeedometerView extends View {

    // Paints pour différents éléments
    private Paint backgroundPaint;
    private Paint zonePaint;
    private Paint circlePaint;
    private Paint tickPaint;
    private Paint textPaint;
    private Paint needlePaint;
    private Paint centerCirclePaint;
    private Paint digitalTextPaint;
    private Paint maxSpeedPaint;

    // Dimensions
    private float centerX;
    private float centerY;
    private float radius;

    // Speed variables
    private float currentSpeed = 0f;
    private float maxSpeed = 120f; // Vitesse maximale affichée
    private float currentAngle = 135f; // Angle actuel de l'aiguille

    // RectF pour les arcs
    private RectF arcBounds;

    public SpeedometerView(Context context) {
        super(context);
        init();
    }

    public SpeedometerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SpeedometerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Initialisation du pinceau pour le fond avec dégradé radial
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setStyle(Paint.Style.FILL);
        Shader radialGradient = new RadialGradient(0, 0, 1, Color.DKGRAY, Color.BLACK, Shader.TileMode.CLAMP);
        backgroundPaint.setShader(radialGradient);

        // Initialisation du pinceau pour les zones de vitesse avec SweepGradient
        zonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        zonePaint.setStyle(Paint.Style.STROKE);
        zonePaint.setStrokeWidth(30f);
        zonePaint.setStrokeCap(Paint.Cap.BUTT);
        // Dégradé de vert à jaune à rouge
        Shader sweepGradient = new SweepGradient(0, 0, new int[]{Color.GREEN, Color.YELLOW, Color.RED, Color.GREEN}, null);
        zonePaint.setShader(sweepGradient);

        // Initialisation du pinceau pour le cercle principal
        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(Color.WHITE);
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(4f);

        // Initialisation du pinceau pour les ticks
        tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickPaint.setColor(Color.WHITE);
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(2f);

        // Initialisation du pinceau pour le texte des ticks
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28f);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);

        // Initialisation du pinceau pour l'aiguille avec ombre
        needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        needlePaint.setColor(Color.GREEN);
        needlePaint.setStyle(Paint.Style.STROKE);
        needlePaint.setStrokeWidth(4f);
        needlePaint.setShadowLayer(4f, 0f, 2f, Color.BLACK);

        // Initialisation du pinceau pour le cercle central
        centerCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerCirclePaint.setColor(Color.GREEN);
        centerCirclePaint.setStyle(Paint.Style.FILL);

        // Initialisation du pinceau pour l'affichage digital de la vitesse
        digitalTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        digitalTextPaint.setColor(Color.WHITE);
        digitalTextPaint.setTextSize(40f);
        digitalTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        digitalTextPaint.setTextAlign(Paint.Align.CENTER);

        // Initialisation du pinceau pour l'indicateur de vitesse maximale
        maxSpeedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maxSpeedPaint.setColor(Color.RED);
        maxSpeedPaint.setStyle(Paint.Style.STROKE);
        maxSpeedPaint.setStrokeWidth(3f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Calculer les dimensions dynamiquement
        centerX = getWidth() / 2f;
        centerY = getHeight() / 2f;
        radius = Math.min(centerX, centerY) - 40f; // Padding de 40f pour éviter le dépassement

        // Définir les limites pour les arcs
        arcBounds = new RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius);

        // Dessiner le fond noir avec dégradé radial
        canvas.save();
        canvas.translate(centerX, centerY);
        canvas.drawCircle(0, 0, radius + 20f, backgroundPaint); // Plus grand pour couvrir les zones colorées
        canvas.restore();

        // Dessiner les zones de vitesse avec SweepGradient
        canvas.save();
        canvas.translate(centerX, centerY);
        canvas.rotate(-135f); // Commencer à l'angle de -135 degrés
        canvas.drawArc(new RectF(-radius, -radius, radius, radius), 0, 270, false, zonePaint);
        canvas.restore();

        // Dessiner le cercle principal (extérieur)
        canvas.drawCircle(centerX, centerY, radius, circlePaint);

        // Dessiner les ticks principaux et mineurs
        drawTicks(canvas, centerX, centerY, radius);

        // Dessiner l’aiguille avec animation
        drawNeedle(canvas, centerX, centerY, radius);

        // Dessiner le cercle central pour masquer la jonction
        canvas.drawCircle(centerX, centerY, 12f, centerCirclePaint);

        // Indiquer la vitesse maximale
        drawMaxSpeedIndicator(canvas, centerX, centerY, radius);

        // Afficher la vitesse actuelle digitalement
        drawDigitalSpeed(canvas, centerX, centerY, radius);
    }

    /**
     * Dessine les ticks principaux et mineurs sur le speedometer.
     */
    private void drawTicks(Canvas canvas, float centerX, float centerY, float radius) {
        int majorTickCount = 6; // Tous les 20 km/h
        int minorTickCount = 3; // 3 ticks mineurs entre les ticks principaux

        float startRadius = radius - 10f;
        float endRadius = radius - 20f;

        for (int i = 0; i <= majorTickCount; i++) {
            float angle = (270f / majorTickCount) * i - 135f; // De -135 à +135 degrés
            double radian = Math.toRadians(angle);
            float sin = (float) Math.sin(radian);
            float cos = (float) Math.cos(radian);

            // Dessiner le tick principal
            float startX = centerX + startRadius * cos;
            float startY = centerY + startRadius * sin;
            float stopX = centerX + endRadius * cos;
            float stopY = centerY + endRadius * sin;
            canvas.drawLine(startX, startY, stopX, stopY, tickPaint);

            // Dessiner le texte du tick principal
            float textRadius = endRadius - 20f;
            float textX = centerX + textRadius * cos;
            float textY = centerY + textRadius * sin + 10f; // Ajustement Y pour centrer le texte
            canvas.drawText(String.valueOf(i * 20), textX, textY, textPaint);

            // Dessiner les ticks mineurs
            if (i < majorTickCount) {
                for (int j = 1; j <= minorTickCount; j++) {
                    float minorAngle = angle + (j * (270f / (majorTickCount * (minorTickCount + 1))));
                    double minorRadian = Math.toRadians(minorAngle);
                    float minorStartX = centerX + (startRadius - 5f) * (float) Math.cos(minorRadian);
                    float minorStartY = centerY + (startRadius - 5f) * (float) Math.sin(minorRadian);
                    float minorStopX = centerX + (endRadius - 5f) * (float) Math.cos(minorRadian);
                    float minorStopY = centerY + (endRadius - 5f) * (float) Math.sin(minorRadian);
                    canvas.drawLine(minorStartX, minorStartY, minorStopX, minorStopY, tickPaint);
                }
            }
        }
    }

    /**
     * Dessine l’aiguille du speedometer.
     */
    private void drawNeedle(Canvas canvas, float centerX, float centerY, float radius) {
        double radian = Math.toRadians(currentAngle);
        float needleLength = radius - 40f; // Ajustez la longueur de l'aiguille
        float needleX = centerX + needleLength * (float) Math.cos(radian);
        float needleY = centerY + needleLength * (float) Math.sin(radian);
        canvas.drawLine(centerX, centerY, needleX, needleY, needlePaint);
    }

    /**
     * Dessine l'indicateur de vitesse maximale.
     */
    private void drawMaxSpeedIndicator(Canvas canvas, float centerX, float centerY, float radius) {
        float angle = 135f - (maxSpeed / maxSpeed) * 270f; // Angle pour la vitesse maximale
        double radian = Math.toRadians(angle);
        float indicatorRadius = radius - 25f;
        float indicatorX = centerX + indicatorRadius * (float) Math.cos(radian);
        float indicatorY = centerY + indicatorRadius * (float) Math.sin(radian);
        canvas.drawLine(centerX, centerY, indicatorX, indicatorY, maxSpeedPaint);

        // Ajouter une flèche ou un marqueur
        Path path = new Path();
        float arrowSize = 15f;
        path.moveTo(indicatorX, indicatorY);
        path.lineTo(indicatorX - arrowSize, indicatorY - arrowSize);
        path.lineTo(indicatorX + arrowSize, indicatorY - arrowSize);
        path.close();
        canvas.drawPath(path, maxSpeedPaint);
    }

    /**
     * Dessine l'affichage digital de la vitesse actuelle.
     */
    private void drawDigitalSpeed(Canvas canvas, float centerX, float centerY, float radius) {
        String speedText = String.valueOf((int) currentSpeed) + " km/h";
        float digitalX = centerX;
        float digitalY = centerY + radius / 2f;
        canvas.drawText(speedText, digitalX, digitalY, digitalTextPaint);
    }

    /**
     * Met à jour la vitesse affichée avec une animation fluide.
     * @param speed La vitesse actuelle en km/h.
     */
    public void setSpeed(float speed) {
        // Limiter la vitesse entre 0 et maxSpeed
        if (speed > maxSpeed) speed = maxSpeed;
        if (speed < 0) speed = 0;

        float targetAngle = 135f - (speed / maxSpeed) * 270f;

        // Animation de l'aiguille
        ValueAnimator animator = ValueAnimator.ofFloat(currentAngle, targetAngle);
        animator.setDuration(300); // Durée de l'animation en millisecondes
        animator.addUpdateListener(animation -> {
            currentAngle = (float) animation.getAnimatedValue();
            invalidate(); // Redessine la vue avec le nouvel angle
        });
        animator.start();

        this.currentSpeed = speed;
    }

    /**
     * Récupère la vitesse actuelle.
     * @return La vitesse actuelle en km/h.
     */
    public float getCurrentSpeed() {
        return currentSpeed;
    }

    /**
     * Définit la vitesse maximale affichée.
     * @param maxSpeed La vitesse maximale en km/h.
     */
    public void setMaxSpeed(float maxSpeed) {
        this.maxSpeed = maxSpeed;
        invalidate();
    }
}
