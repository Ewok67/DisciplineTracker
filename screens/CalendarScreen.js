import React from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';

export default function CalendarScreen({ tasks }) {
  const days = ['MON','TUE','WED','THU','FRI','SAT','SUN'];
  const today = new Date().getDate();

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.subtitle}>Weekly Overview</Text>
      <View style={styles.grid}>
        {days.map((d, i) => {
          const rate = Math.round(Math.random() * 100); // placeholder for completion %
          const color = rate >= 80 ? '#00ffa3' : rate >= 50 ? '#ffaa00' : '#ff3b3b';
          return (
            <View key={d} style={styles.card}>
              <Text style={styles.day}>{d}</Text>
              <Text style={[styles.percent, { color }]}>{rate}%</Text>
              <Text style={styles.label}>completed</Text>
            </View>
          );
        })}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  subtitle: { color: '#8899aa', fontSize: 14, marginBottom: 12, letterSpacing: 1 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between' },
  card: { width: '30%', backgroundColor: '#0d1b2a', borderRadius: 14, padding: 12, marginBottom: 10, borderWidth: 1, borderColor: '#1a2a3a', alignItems: 'center' },
  day: { color: '#e2f3ff', fontSize: 12, fontWeight: 'bold', letterSpacing: 1, marginBottom: 6 },
  percent: { fontSize: 22, fontWeight: 'bold' },
  label: { color: '#556677', fontSize: 10 },
});
