import React from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';

export default function DashboardScreen({ tasks }) {
  return (
    <ScrollView style={styles.container}>
      <Text style={styles.subtitle}>Skip Analytics</Text>
      <View style={styles.row}>
        {['Today','Week','Month','Year'].map(p => (
          <View key={p} style={styles.card}>
            <Text style={styles.period}>{p}</Text>
            <Text style={styles.stat}>3 skips</Text>
          </View>
        ))}
      </View>
      <Text style={styles.subtitle}>Completion Metrics</Text>
      <View style={styles.cardBig}>
        <Text style={styles.metric}>Completion Rate: 72%</Text>
        <Text style={styles.metric}>Avg Time to Complete: 2h 14m</Text>
      </View>
      <Text style={styles.subtitle}>Priority Overview</Text>
      <View style={styles.row}>
        <View style={[styles.tag, { backgroundColor: '#ffaa00' }]}><Text style={styles.tagText}>Important • 5</Text></View>
        <View style={[styles.tag, { backgroundColor: '#ff3b3b' }]}><Text style={styles.tagText}>Very Important • 3</Text></View>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  subtitle: { color: '#8899aa', fontSize: 14, marginBottom: 12, letterSpacing: 1 },
  row: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between' },
  card: { width: '48%', backgroundColor: '#0d1b2a', borderRadius: 14, padding: 14, marginBottom: 10, borderWidth: 1, borderColor: '#1a2a3a' },
  period: { color: '#e2f3ff', fontWeight: 'bold', fontSize: 12 },
  stat: { color: '#ff6b35', fontSize: 16, fontWeight: 'bold', marginTop: 4 },
  cardBig: { backgroundColor: '#0d1b2a', borderRadius: 14, padding: 18, borderWidth: 1, borderColor: '#1a2a3a', marginBottom: 10 },
  metric: { color: '#e2f3ff', fontSize: 16, fontWeight: 'bold', marginVertical: 4 },
  tag: { paddingHorizontal: 14, paddingVertical: 8, borderRadius: 20, marginBottom: 8 },
  tagText: { color: '#fff', fontWeight: 'bold', fontSize: 12 },
});
