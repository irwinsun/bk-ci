<template>
    <div
        class="devops-iframe-content"
        :class="{ 'showTopPrompt': showAnnounce }"
    >
        <div
            v-if="isAnyPopupShow"
            class="iframe-over-layout"
        />
        <div
            v-bkloading="{ isLoading }"
            :style="{ height: '100%' }"
        >
            <iframe
                v-if="src"
                id="iframe-box"
                ref="iframeEle"
                allowfullscreen
                allow="clipboard-read; clipboard-write"
                :src="src"
                @load="onLoad"
            />
        </div>
    </div>
</template>

<script lang="ts">
    import Vue from 'vue'
    import { Component, Watch } from 'vue-property-decorator'
    import eventBus from '../utils/eventBus'
    import { urlJoin, queryStringify, getServiceAliasByPath } from '../utils/util'
    import { State, Getter } from 'vuex-class'
    import cookie from 'js-cookie'

    Component.registerHooks([
        'beforeRouteEnter',
        'beforeRouteLeave',
        'beforeRouteUpdate'
    ])

    @Component()
    export default class IframeView extends Vue {
        isLoading: boolean = true
        initPath: string = ''
        src: string = ''
        leaving: boolean = false

        $refs: {
            iframeEle: HTMLIFrameElement
        }

        @State projectList
        @State currentPage
        @State isAnyPopupShow
        @State user
        @State headerConfig
        @Getter showAnnounce

        created () {
            this.init()
            eventBus.$on('goHome', this.backHome) // 触发返回首页事件
        }

        beforeRouteLeave (to, from, next) {
            if (!this.leaving && location.href.indexOf('pipeline') > -1 && (location.href.indexOf('edit') > -1 || location.href.indexOf('setting') > -1)) {
                this.leaveConfirm(to, from, next)
                return
            }
            next()
        }
        
        leaveConfirm (to, from, next) {
            this.leaving = true
            this.$bkInfo({
                type: 'warning',
                theme: 'warning',
                title: this.$t('leaveConfirmTitle'),
                subTitle: this.$t('leaveConfirmMsg'),
                confirmFn: () => {
                    this.src = null
                    this.$nextTick(() => {
                        next(true)
                    })
                },
                cancelFn: () => {
                    next(false)
                    this.leaving = false
                }
            })
        }

        beforeDestroy () {
            this.leaving = false
        }

        get needLoading (): boolean {
            return this.$route.name === 'job'
        }

        backHome () {
            if (this.needLoading) {
                this.isLoading = true
            }
            if (this.$refs.iframeEle) {
                this.iframeUtil.goHome(this.$refs.iframeEle.contentWindow)
            }
        }

        init () {
            const { showProjectList } = this.headerConfig
            const { projectIdType } = this.$route.meta
            const query = queryStringify(this.$route.query)
            const path = this.$route.path.replace('/console', '')
            const hash = this.$route.hash
            
            if (showProjectList) {
                const reg = /^\/?\w+\/(([\w-]+)\/?)(\S*)\/?$/
                const matchResult = path.match(reg)
                const { projectId } = this.$route.params
                const initPath = matchResult ? matchResult[3] : ''

                if (projectIdType === 'path') {
                    this.src = urlJoin(this.currentPage.iframe_url, projectId, initPath) + `${query ? '?' + query : ''}` + hash
                } else {
                    const query = Object.assign(this.$route.query, {
                        projectId
                    })
                    this.src = urlJoin(this.currentPage.iframe_url, initPath) + '?' + queryStringify(query) + hash
                }
            } else {
                const reg = /^\/?\w+\/(\S*)\/?$/
                const initPath = path.match(reg) ? path.replace(reg, '$1') : ''
                const query = Object.assign(
                    this.currentPage.link === '/permission/' ? {} : { project_code: cookie.get(X_DEVOPS_PROJECT_ID) },
                    this.$route.query
                )
                this.src = urlJoin(this.currentPage.iframe_url, initPath) + '?' + queryStringify(query) + hash
            }
        }

        onLoad () {
            this.isLoading = false
            if (this.$refs.iframeEle) {
                const childWin = this.$refs.iframeEle.contentWindow
                this.iframeUtil.syncProjectList(childWin, this.projectList)
                this.iframeUtil.syncUserInfo(childWin, this.user)
                this.iframeUtil.syncLocale(childWin, this.$i18n.locale)
                
                if (this.$route.params.projectId) {
                    this.iframeUtil.syncProjectId(childWin, this.$route.params.projectId)
                }
            }
        }

        isSameModule (newPath: string, oldPath: string): boolean {
            return getServiceAliasByPath(newPath) === getServiceAliasByPath(oldPath)
        }

        @Watch('$route')
        routeChange (newRoute: ObjectMap, oldRoute: ObjectMap): void {
            const { path, params } = newRoute
            const { path: oldPath, params: oldParams } = oldRoute
            if (!this.isSameModule(path, oldPath)) {
                this.isLoading = true
                this.init()
            } else if (params.projectId !== oldParams.projectId) {
                if (this.$refs.iframeEle && params.projectId) { // 将当前projectId同步到子窗口
                    this.iframeUtil.syncProjectId(this.$refs.iframeEle.contentWindow, params.projectId)
                }
            }
        }

        @Watch('projectList')
        handleProjectListChange (projectList, oldList) {
            if (this.$refs.iframeEle) {
                const childWin = this.$refs.iframeEle.contentWindow
                this.iframeUtil.syncProjectList(childWin, projectList)
            }
        }

        @Watch('user')
        handleUserChange (user) {
            if (this.$refs.iframeEle) {
                const childWin = this.$refs.iframeEle.contentWindow
                this.iframeUtil.syncUserInfo(childWin, user)
            }
        }

        @Watch('$i18n.locale')
        handleLocaleChange (locale) {
            if (this.$refs.iframeEle) {
                const childWin = this.$refs.iframeEle.contentWindow
                this.iframeUtil.syncLocale(childWin, locale)
            }
        }
    }
</script>

<style lang="scss">
    @import '../assets/scss/conf';
    .devops-iframe-content {
        position: absolute;
        left: 0;
        top: $headerHeight;
        right: 0;
        bottom: 0;
        overflow: hidden;
        min-width: 1280px;
        .iframe-over-layout {
            height: 100%;
            width: 100%;
            position: absolute;
            z-index: 2;
        }
        iframe {
            width: 100%;
            min-height: 100%;
            border: 0;
        }
    }
    .showTopPrompt {
        top: 81px;
    }
</style>
