import api from './api.js';

export async function listRules() {
  const { data } = await api.get('/rules');
  return data.data;
}

export async function setRuleEnabled(id, enabled) {
  const { data } = await api.patch(`/rules/${id}/enabled`, { enabled });
  return data.data;
}

export async function updateRule(id, patch) {
  const { data } = await api.put(`/rules/${id}`, patch);
  return data.data;
}

export async function backtestRule(id, body) {
  const { data } = await api.post(`/rules/${id}/backtest`, body);
  return data.data;
}

export async function getTuningSuggestions() {
  const { data } = await api.get('/rules/tuning-suggestions');
  return data.data;
}

export async function listRuleTypes() {
  const { data } = await api.get('/rules/types');
  return data.data;
}

export async function listBuildingBlocks() {
  const { data } = await api.get('/building-blocks');
  return data.data;
}

export async function getBuildingBlockVocabulary() {
  const { data } = await api.get('/building-blocks/vocabulary');
  return data.data; // { fields, operators }
}

export async function createBuildingBlock(body) {
  const { data } = await api.post('/building-blocks', body);
  return data.data;
}

export async function updateBuildingBlock(id, body) {
  const { data } = await api.put(`/building-blocks/${id}`, body);
  return data.data;
}

export async function deleteBuildingBlock(id) {
  await api.delete(`/building-blocks/${id}`);
}
