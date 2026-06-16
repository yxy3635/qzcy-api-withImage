<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useIntersectionObserver } from '@vueuse/core'
import ThreeBackground from '@/components/ThreeBackground.vue'
import { useAuthStore } from '@/store/authStore'
import showImage1 from '@/assets/images/showImage1.png'
import showImage2 from '@/assets/images/showImage2.png'
import showImage3 from '@/assets/images/showImage3.png'
import showImage4 from '@/assets/images/showImage4.png'
import showImage5 from '@/assets/images/showImage5.png'	
import showImage6 from '@/assets/images/showImage6.png'

const router = useRouter()
const auth = useAuthStore()

const primaryTarget = computed(() => {
  if (!auth.isAuthenticated) return '/register'
  return auth.role === 'ADMIN' ? '/admin/dashboard' : '/create'
})

const gallery = [
  { src: showImage1, title: '写实人物', desc: '冷调电影光，玻璃反射，精致面部细节' },
  { src: showImage2, title: '抽象景观', desc: '流体色彩与超现实空间层次' },
  { src: showImage3, title: '虚幻梦境', desc: '超现实场景与抽象元素' },
  { src: showImage4, title: '概念艺术', desc: '叙事场景与高级色彩控制' },
  { src: showImage5, title: '未来科技', desc: '高对比科技视觉与精密结构' },
  { src: showImage6, title: '视觉实验', desc: '商业摄影级材质与布光' }
]

const gallery0 = computed(() => gallery[0]!)
const gallery1 = computed(() => gallery[1]!)
const gallery2 = computed(() => gallery[2]!)
const gallery3 = computed(() => gallery[3]!)
const gallery4 = computed(() => gallery[4]!)
const gallery5 = computed(() => gallery[5]!)

function scrollToGallery() {
  window.document.getElementById('bento-gallery')?.scrollIntoView({ behavior: 'smooth' })
}

// Scroll Animation Logic
const heroRef = ref(null)
const heroVisible = ref(false)
useIntersectionObserver(heroRef, ([entry]) => {
  if (entry?.isIntersecting) heroVisible.value = true
})

const galleryHeaderRef = ref(null)
const galleryHeaderVisible = ref(false)
useIntersectionObserver(galleryHeaderRef, ([entry]) => {
  if (entry?.isIntersecting) galleryHeaderVisible.value = true
})

const bentoItemRefs = ref<HTMLElement[]>([])
const bentoItemsVisible = ref<boolean[]>(new Array(6).fill(false))
const setBentoItemRef = (el: any) => {
  if (el && !bentoItemRefs.value.includes(el)) {
    bentoItemRefs.value.push(el)
    const index = bentoItemRefs.value.length - 1
    useIntersectionObserver(el, ([entry]) => {
      if (entry?.isIntersecting) {
        setTimeout(() => {
          bentoItemsVisible.value[index] = true
        }, index * 100)
      }
    })
  }
}

const title = "imageCreater"

const routerToGithub = () => {
	window.open('https://github.com/yxy3635/imageCreater-gptImage2' , '_blank')
}
</script>

<template>
  <div class="relative w-full min-h-screen bg-[#F8FAFC] text-slate-900 overflow-x-hidden font-sans selection:bg-blue-500 selection:text-white">
    
    <!-- Floating Minimalist Navigation -->
    <nav class="fixed left-1/2 top-3 z-50 flex w-[calc(100%-24px)] max-w-5xl -translate-x-1/2 items-center justify-between rounded-full border border-white/40 bg-white/70 px-4 py-3 shadow-[0_8px_32px_rgba(0,0,0,0.04)] backdrop-blur-xl transition-all hover:bg-white/90 sm:top-6 sm:w-[90%] sm:px-6">
      <div class="flex cursor-pointer items-center gap-2 text-lg font-black tracking-tight text-slate-900 sm:text-xl" @click="router.push('/')">
        <span>{{ title }}</span>
      </div>
      
      <div class="hidden md:flex items-center gap-8 text-sm font-medium text-slate-500">
        <button class="hover:text-blue-600 transition-colors" @click="router.push('/relay')">中转站</button>
        <button class="hover:text-blue-600 transition-colors" @click="scrollToGallery">视觉矩阵</button>
        <button class="hover:text-blue-600 transition-colors">对接文档</button>
		<button class="hover:text-blue-600 transition-colors" 
			style="color: aqua;"
			@click="routerToGithub">
			github
		</button>
      </div>

      <button 
        class="rounded-full bg-slate-900 px-4 py-2 text-xs font-semibold text-white shadow-md transition-colors hover:bg-blue-600 hover:shadow-blue-500/30 sm:px-5 sm:text-sm"
        @click="router.push(primaryTarget)"
      >
        进入控制台
      </button>
    </nav>

    <!-- Hero Section -->
    <section class="relative flex min-h-[760px] w-full flex-col items-center justify-center overflow-hidden sm:h-screen sm:min-h-[680px]">
      <!-- 3D Particle Wave Background -->
      <div class="absolute inset-0 z-0 bg-gradient-to-b from-white via-transparent to-[#F8FAFC]">
        <ThreeBackground />
      </div>

      <!-- Hero Content -->
      <div ref="heroRef" class="relative z-10 mt-20 flex flex-col items-center px-4 text-center sm:px-6">
        
        <h1 
          class="max-w-5xl text-4xl font-black leading-[1.15] tracking-tight text-slate-900 transition-all delay-150 duration-1000 sm:text-5xl md:text-7xl lg:text-8xl"
          :class="heroVisible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-8'"
        >
          想象力，<br class="md:hidden" />
          <span class="text-transparent bg-clip-text bg-gradient-to-r from-blue-600 via-cyan-500 to-indigo-600">
            即刻具象。
          </span>
        </h1>
        
        <p 
          class="mt-6 max-w-2xl text-base font-medium leading-relaxed text-slate-500 transition-all delay-300 duration-1000 sm:mt-8 md:text-xl"
          :class="heroVisible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-8'"
        >
          下一代 AI 视觉合成引擎。无需繁琐调试，将您的文本灵感在毫秒间转化为超写实摄影、概念艺术与商业级视觉资产。
        </p>
        
        <div 
          class="mt-12 flex flex-col sm:flex-row items-center gap-4 transition-all duration-1000 delay-500 transform"
          :class="heroVisible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-8'"
        >
          <button 
            class="group relative flex items-center justify-center gap-2 bg-slate-900 text-white text-base font-semibold px-8 py-4 rounded-full overflow-hidden transition-transform hover:scale-105 shadow-[0_20px_40px_-10px_rgba(0,0,0,0.3)]"
            @click="router.push(primaryTarget)"
          >
            <div class="absolute inset-0 bg-gradient-to-r from-blue-600 to-indigo-600 opacity-0 group-hover:opacity-100 transition-opacity duration-300"></div>
            <span class="relative z-10">立即开始生成</span>
            <svg class="relative z-10 w-4 h-4 transition-transform group-hover:translate-x-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 5l7 7m0 0l-7 7m7-7H3" />
            </svg>
          </button>
          
          <button 
            class="bg-white/80 backdrop-blur border border-slate-200 text-slate-700 text-base font-semibold px-8 py-4 rounded-full hover:bg-white hover:shadow-lg transition-all duration-300"
            @click="scrollToGallery"
          >
            探索画廊
          </button>
        </div>
      </div>

      <!-- Scroll Indicator -->
      <div 
        class="absolute bottom-10 left-1/2 -translate-x-1/2 flex flex-col items-center gap-2 opacity-50 transition-all duration-1000 delay-700 transform"
        :class="heroVisible ? 'opacity-50 translate-y-0' : 'opacity-0 translate-y-4'"
      >
        <span class="text-xs font-bold tracking-widest uppercase">向下滑动</span>
        <div class="w-[1px] h-8 bg-gradient-to-b from-slate-900 to-transparent"></div>
      </div>
    </section>

    <!-- Bento Box Gallery Section -->
    <section id="bento-gallery" class="relative z-20 bg-[#F8FAFC] px-4 py-20 sm:px-6 md:py-32">
      <div class="max-w-7xl mx-auto">
        
        <div ref="galleryHeaderRef" class="mb-10 md:mb-16 md:text-center">
          <h2 
            class="mb-4 text-3xl font-black tracking-tight text-slate-900 transition-all duration-1000 sm:text-4xl md:mb-6 md:text-5xl lg:text-6xl"
            :class="galleryHeaderVisible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-12'"
          >多维视觉矩阵</h2>
          <p 
            class="text-lg text-slate-500 max-w-2xl mx-auto font-medium transition-all duration-1000 delay-200 transform"
            :class="galleryHeaderVisible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-12'"
          >
            探索由核心引擎驱动的精选风格网络。从电影级写实到超现实架构，满足全场景、全链路的视觉工业需求。
          </p>
        </div>

        <!-- Bento Grid Layout -->
        <div class="grid grid-cols-1 gap-4 md:grid-cols-4 md:auto-rows-[300px]">
          
          <!-- Image 1: Large Feature (2x2) -->
          <div 
            :ref="setBentoItemRef"
            class="group relative h-80 overflow-hidden rounded-3xl bg-white shadow-sm transition-all duration-700 hover:shadow-2xl md:col-span-2 md:row-span-2 md:h-auto"
            :class="bentoItemsVisible[0] ? 'opacity-100 translate-y-0 scale-100' : 'opacity-0 translate-y-16 scale-95'"
          >
            <img :src="gallery0.src" :alt="gallery0.title" class="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105" />
            <div class="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent opacity-60 group-hover:opacity-80 transition-opacity"></div>
            <div class="absolute bottom-8 left-8 right-8 text-white transform translate-y-4 group-hover:translate-y-0 transition-transform duration-500">
              <p class="text-xs font-bold tracking-widest text-blue-400 mb-2 uppercase">核心推荐</p>
              <h3 class="text-3xl font-black mb-2">{{ gallery0.title }}</h3>
              <p class="text-sm text-white/80 font-medium">{{ gallery0.desc }}</p>
            </div>
          </div>

          <!-- Image 2: Standard (1x1) -->
          <div 
            :ref="setBentoItemRef"
            class="group relative h-64 overflow-hidden rounded-3xl bg-white shadow-sm transition-all duration-700 hover:shadow-xl md:col-span-1 md:row-span-1 md:h-auto"
            :class="bentoItemsVisible[1] ? 'opacity-100 translate-y-0 scale-100' : 'opacity-0 translate-y-16 scale-95'"
          >
            <img :src="gallery1.src" :alt="gallery1.title" class="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105" />
            <div class="absolute inset-0 bg-gradient-to-t from-black/80 to-transparent opacity-60 group-hover:opacity-80 transition-opacity"></div>
            <div class="absolute bottom-6 left-6 right-6 text-white">
              <h3 class="text-xl font-bold mb-1">{{ gallery1.title }}</h3>
              <p class="text-xs text-white/80 line-clamp-1">{{ gallery1.desc }}</p>
            </div>
          </div>

          <!-- Image 3: Standard (1x1) -->
          <div 
            :ref="setBentoItemRef"
            class="group relative h-64 overflow-hidden rounded-3xl bg-white shadow-sm transition-all duration-700 hover:shadow-xl md:col-span-1 md:row-span-1 md:h-auto"
            :class="bentoItemsVisible[2] ? 'opacity-100 translate-y-0 scale-100' : 'opacity-0 translate-y-16 scale-95'"
          >
            <img :src="gallery2.src" :alt="gallery2.title" class="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105" />
            <div class="absolute inset-0 bg-gradient-to-t from-black/80 to-transparent opacity-60 group-hover:opacity-80 transition-opacity"></div>
            <div class="absolute bottom-6 left-6 right-6 text-white">
              <h3 class="text-xl font-bold mb-1">{{ gallery2.title }}</h3>
              <p class="text-xs text-white/80 line-clamp-1">{{ gallery2.desc }}</p>
            </div>
          </div>

          <!-- Image 4: Wide (2x1) -->
          <div 
            :ref="setBentoItemRef"
            class="group relative h-64 overflow-hidden rounded-3xl bg-white shadow-sm transition-all duration-700 hover:shadow-xl md:col-span-2 md:row-span-1 md:h-auto"
            :class="bentoItemsVisible[3] ? 'opacity-100 translate-y-0 scale-100' : 'opacity-0 translate-y-16 scale-95'"
          >
            <img :src="gallery3.src" :alt="gallery3.title" class="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105" />
            <div class="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent opacity-60 group-hover:opacity-80 transition-opacity"></div>
            <div class="absolute bottom-6 left-6 right-6 text-white">
              <h3 class="text-2xl font-bold mb-1">{{ gallery3.title }}</h3>
              <p class="text-sm text-white/80">{{ gallery3.desc }}</p>
            </div>
          </div>

          <!-- Image 5: Wide (2x1) -->
          <div 
            :ref="setBentoItemRef"
            class="group relative h-64 overflow-hidden rounded-3xl bg-white shadow-sm transition-all duration-700 hover:shadow-xl md:col-span-2 md:row-span-1 md:h-auto"
            :class="bentoItemsVisible[4] ? 'opacity-100 translate-y-0 scale-100' : 'opacity-0 translate-y-16 scale-95'"
          >
            <img :src="gallery4.src" :alt="gallery4.title" class="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105" />
            <div class="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent opacity-60 group-hover:opacity-80 transition-opacity"></div>
            <div class="absolute bottom-6 left-6 right-6 text-white">
              <h3 class="text-2xl font-bold mb-1">{{ gallery4.title }}</h3>
              <p class="text-sm text-white/80">{{ gallery4.desc }}</p>
            </div>
          </div>

          <!-- Image 6: Wide (2x1) -->
          <div 
            :ref="setBentoItemRef"
            class="group relative h-64 overflow-hidden rounded-3xl bg-white shadow-sm transition-all duration-700 hover:shadow-xl md:col-span-2 md:row-span-1 md:h-auto"
            :class="bentoItemsVisible[5] ? 'opacity-100 translate-y-0 scale-100' : 'opacity-0 translate-y-16 scale-95'"
          >
            <img :src="gallery5.src" :alt="gallery5.title" class="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105" />
            <div class="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent opacity-60 group-hover:opacity-80 transition-opacity"></div>
            <div class="absolute bottom-6 left-6 right-6 text-white">
              <h3 class="text-2xl font-bold mb-1">{{ gallery5.title }}</h3>
              <p class="text-sm text-white/80">{{ gallery5.desc }}</p>
            </div>
          </div>

        </div>
      </div>
    </section>

    <!-- Footer -->
    <footer class="bg-white border-t border-slate-200/60 py-12 px-6">
      <div class="max-w-7xl mx-auto flex flex-col md:flex-row items-center justify-between gap-6">
        <div class="flex items-center gap-3">
          <span class="font-black text-xl text-slate-900 tracking-tight">imageCreater</span>
        </div>
        <p class="text-slate-400 font-medium text-sm">
          &copy; {{ new Date().getFullYear() }} All rights reserved. 为未来视觉而生。
        </p>
      </div>
    </footer>
  </div>
</template>
