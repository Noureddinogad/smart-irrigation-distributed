import React, { useState } from 'react';
import { Sparkles } from 'lucide-react';
import { GoogleGenAI } from '@google/genai';
import { SensorData } from '../types';

interface GeminiInsightProps {
  data: SensorData;
}

const GeminiInsight: React.FC<GeminiInsightProps> = ({ data }) => {
  const [insight, setInsight] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  // Note: In a real app, API_KEY should be in process.env. 
  // For this demo structure, we assume it's available or mocked if not provided.
  const getInsight = async () => {
    if (!process.env.API_KEY) {
        setInsight("Simulation: Soil moisture is optimal for current temperature. No immediate irrigation needed to conserve water.");
        return;
    }

    setLoading(true);
    try {
      const ai = new GoogleGenAI({ apiKey: process.env.API_KEY });
      const response = await ai.models.generateContent({
        model: 'gemini-2.5-flash',
        contents: `Analyze this irrigation data: Moisture ${data.moisture}%, Temp ${data.temperature}C, Humidity ${data.humidity}%. Give a 1-sentence recommendation for a gardener.`,
      });
      setInsight(response.text);
    } catch (error) {
      console.error(error);
      setInsight("Unable to fetch AI insights at this moment.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mt-4">
      {!insight && !loading && (
        <button 
            onClick={getInsight}
            className="flex items-center justify-center w-full py-2 text-xs font-medium text-emerald-600 bg-emerald-50 rounded-lg hover:bg-emerald-100 transition-colors border border-emerald-100"
        >
            <Sparkles className="w-3 h-3 mr-2" />
            Get Smart AI Insight
        </button>
      )}
      
      {loading && (
        <div className="flex items-center justify-center py-2 text-xs text-gray-400 animate-pulse">
            <Sparkles className="w-3 h-3 mr-2" />
            Analyzing sensor data...
        </div>
      )}

      {insight && (
        <div className="bg-gradient-to-r from-emerald-50 to-teal-50 p-3 rounded-lg border border-emerald-100 text-xs text-emerald-800 flex items-start animate-fade-in">
            <Sparkles className="w-4 h-4 mr-2 text-emerald-600 flex-shrink-0 mt-0.5" />
            <p>{insight}</p>
        </div>
      )}
    </div>
  );
};

export default GeminiInsight;