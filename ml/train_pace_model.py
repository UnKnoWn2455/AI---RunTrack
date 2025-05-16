import numpy as np
import tensorflow as tf
from tensorflow.keras.layers import Input, Dense, Conv1D, LayerNormalization, Dropout, GlobalAveragePooling1D
from tensorflow.keras.models import Model

# Constants matching the Kotlin implementation
SEQUENCE_LENGTH = 10
NUM_FEATURES = 4
NUM_SAMPLES = 10000  # Number of synthetic training samples

def create_tcn_model():
    inputs = Input(shape=(SEQUENCE_LENGTH, NUM_FEATURES))
    
    # First TCN block
    x = Conv1D(filters=32, kernel_size=3, padding='causal', dilation_rate=1, activation='relu')(inputs)
    x = LayerNormalization()(x)
    x = Dropout(0.1)(x)
    
    # Second TCN block with increased dilation
    x = Conv1D(filters=32, kernel_size=3, padding='causal', dilation_rate=2, activation='relu')(x)
    x = LayerNormalization()(x)
    x = Dropout(0.1)(x)
    
    # Third TCN block with increased dilation
    x = Conv1D(filters=32, kernel_size=3, padding='causal', dilation_rate=4, activation='relu')(x)
    x = LayerNormalization()(x)
    
    # Global pooling and dense layers
    x = GlobalAveragePooling1D()(x)
    x = Dense(16, activation='relu')(x)
    outputs = Dense(1)(x)  # Single output for pace prediction
    
    model = Model(inputs=inputs, outputs=outputs)
    return model

def generate_synthetic_data(num_samples):
    # Generate base patterns
    time = np.linspace(0, num_samples/100, num_samples)
    
    # Initialize arrays
    X = np.zeros((num_samples, SEQUENCE_LENGTH, NUM_FEATURES))
    y = np.zeros((num_samples, 1))
    
    for i in range(num_samples):
        # Generate realistic running patterns
        base_pace = 5 + np.random.random() * 3  # Base pace between 5-8 min/km
        
        for j in range(SEQUENCE_LENGTH):
            t = time[i] + j/60
            # Add variations to pace
            variation = np.sin(t) * 0.5 + np.random.random() * 0.3
            current_pace = base_pace + variation
            
            # Calculate speed and acceleration
            speed = 16.67 / current_pace
            acceleration = 0 if j == 0 else (speed - (16.67 / X[i,j-1,0])) / 1.0
            
            # Generate altitude
            altitude = 100 + np.sin(t * 0.1) * 20 + np.random.random() * 5
            
            X[i,j] = [current_pace, speed, acceleration, altitude]
        
        # Generate target (next pace)
        recent_paces = X[i,:,0][-3:]
        trend = np.mean(recent_paces)
        y[i] = trend + np.random.random() * 0.2 - 0.1
    
    return X, y

def main():
    # Generate synthetic data
    X_train, y_train = generate_synthetic_data(NUM_SAMPLES)
    
    # Create and compile model
    model = create_tcn_model()
    model.compile(
        optimizer='adam',
        loss='mse',
        metrics=['mae']
    )
    
    # Train model
    model.fit(
        X_train, y_train,
        epochs=50,
        batch_size=32,
        validation_split=0.2
    )
    
    # Convert to TFLite with quantization
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]  # Use float16 for better performance
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.TFLITE_BUILTINS_INT8
    ]
    
    # Representative dataset for quantization
    def representative_dataset():
        for i in range(100):
            yield [X_train[i:i+1].astype(np.float32)]
    
    converter.representative_dataset = representative_dataset
    
    # Convert and save the model
    tflite_model = converter.convert()
    
    # Save the TFLite model
    with open('../app/src/main/assets/pace_prediction.tflite', 'wb') as f:
        f.write(tflite_model)
    
    # Save a test dataset for validation
    np.save('../app/src/main/assets/test_data.npy', {
        'X_test': X_train[:100],
        'y_test': y_train[:100]
    })

if __name__ == '__main__':
    main() 